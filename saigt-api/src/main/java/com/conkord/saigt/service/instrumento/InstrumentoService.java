package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.CriarInstrumentoRequest;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.IgtSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.integracao.snit.MunicipioIgtSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Ponto único de escrita/leitura de `instrumento`; visibilidade e autorização vêm sempre de {@code AutorizacaoService}. */
@Service
public class InstrumentoService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentoService.class);
    private static final Pattern DTCC_VALIDO = Pattern.compile("\\d{4}");
    private static final String TIPO_OCORRENCIA = "INSTRUMENTO";
    private static final String ACAO_CRIACAO = "CRIACAO";
    private static final String ACAO_EM_VIGOR = "ATUALIZACAO_EM_VIGOR";
    private static final List<String> CODIGOS_QUE_PUBLICAM = List.of("ELABORACAO", "REVISAO", "DELIMITACAO");
    private static final String CODIGO_REVOGACAO = "REVOGACAO";
    private static final String MENSAGEM_CONCELHOS = "Indique o concelho ou os concelhos do instrumento.";
    private static final String CATEGORIA_SRUP = "SRUP";
    private static final String CAMPO_INSTRUMENTO_ORIGEM = "instrumentoOrigemId";
    private static final String RESTRICAO_IGT_SNIT_UNICO = "uq_instrumento_id_igt_snit";

    private final InstrumentoRepository instrumentoRepository;
    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;
    private final InstrumentoSnitService instrumentoSnitService;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final TerritorioLocalService territorioLocalService;
    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ListaValoresRepository listaValoresRepository;
    private final TransactionTemplate transacao;

    public InstrumentoService(InstrumentoRepository instrumentoRepository, TipoInstrumentoRepository tipoInstrumentoRepository,
                               InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                               UtilizadorRepository utilizadorRepository,
                               AutorizacaoService autorizacaoService, ContextoUtilizadorResolver contextoUtilizadorResolver,
                               AuditoriaService auditoriaService, InstrumentoSnitService instrumentoSnitService,
                               InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                               TerritorioLocalService territorioLocalService,
                               SnitReferenciaRepository snitReferenciaRepository,
                               ListaValoresRepository listaValoresRepository,
                               PlatformTransactionManager transactionManager) {
        this.listaValoresRepository = listaValoresRepository;
        this.transacao = new TransactionTemplate(transactionManager);
        this.territorioLocalService = territorioLocalService;
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
        this.instrumentoSnitService = instrumentoSnitService;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
    }

    /**
     * `responsavelId`/`criadoPor` são sempre o utilizador do token; `responsavelId` não tem função de
     * autorização. Com `idIgtSnit`, o IGT é consultado ao SNIT depois da autorização e antes de abrir a transação
     * da escrita, para a chamada HTTP não segurar uma ligação à base de dados. Chamado dentro de uma transação
     * (ex.: a Elaboração), junta-se a ela.
     */
    public Instrumento criar(CriarInstrumentoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.findById(request.tipoInstrumentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Instrumento não encontrado."));
        autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento);

        IgtSnit igt = request.idIgtSnit() == null ? null
                : instrumentoSnitService.obterParaAdocao(request.idIgtSnit(), tipoInstrumento);
        return transacao.execute(estado -> gravar(request, contexto, tipoInstrumento, igt));
    }

    private Instrumento gravar(CriarInstrumentoRequest request, ContextoUtilizador contexto,
                               TipoInstrumento tipoInstrumento, IgtSnit igt) {
        Utilizador utilizador = utilizadorRepository.findById(contexto.utilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));
        ListaValores tipologiaSrup = tipologiaSrupValidada(tipoInstrumento, request.tipologiaSrupId());

        String designacao = igt == null ? request.designacao() : InstrumentoSnitService.designacaoLocal(igt);
        List<String> concelhosPedidos = igt == null ? concelhosValidados(tipoInstrumento, designacao, request.concelhos()) : null;
        Instrumento instrumentoOrigem = instrumentoOrigemValidado(tipoInstrumento, request.instrumentoOrigemId());

        // Formato do número por decidir: até lá é a sequência em texto.
        String numero = String.valueOf(instrumentoRepository.proximoNumeroSequencial());
        Instrumento instrumento = new Instrumento(tipoInstrumento, numero, designacao, utilizador, utilizador, instrumentoOrigem);
        instrumento.setTipologiaSrup(tipologiaSrup);
        if (igt != null) {
            instrumento.setIdIgtSnit(request.idIgtSnit());
            instrumento.setEmVigor(true);
        }

        Instrumento guardado = guardar(instrumento);
        List<String> concelhos = igt == null ? gravarConcelhos(guardado, concelhosPedidos) : gravarConcelhosDoIgt(guardado, igt);

        Map<String, Object> dadosAuditoria = instrumentoOrigem == null ? Map.of("dtcc", concelhos)
                : Map.of("dtcc", concelhos, "instrumentoOrigemId", instrumentoOrigem.getId().toString());
        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_CRIACAO,
                utilizador.getId(), contexto.entidadeId(), null, dadosAuditoria);

        return guardado;
    }

    /** Inexistente ou não servidão dão a mesma resposta, para não revelar que o Instrumento existe. */
    private Instrumento instrumentoOrigemValidado(TipoInstrumento tipoInstrumento, UUID instrumentoOrigemId) {
        if (instrumentoOrigemId == null) {
            return null;
        }
        if (!CATEGORIA_SRUP.equals(tipoInstrumento.getCategoria())) {
            throw campoInvalido(CAMPO_INSTRUMENTO_ORIGEM, "Só uma servidão pode indicar uma servidão de origem.");
        }
        return instrumentoRepository.findById(instrumentoOrigemId)
                .filter(origem -> CATEGORIA_SRUP.equals(origem.getTipoInstrumento().getCategoria()))
                .orElseThrow(() -> campoInvalido(CAMPO_INSTRUMENTO_ORIGEM, "Servidão de origem inválida."));
    }

    /** Opcional na API: a obrigatoriedade no formulário das servidões é do ecrã. */
    private ListaValores tipologiaSrupValidada(TipoInstrumento tipoInstrumento, Long tipologiaSrupId) {
        if (tipologiaSrupId == null) {
            return null;
        }
        if (!CATEGORIA_SRUP.equals(tipoInstrumento.getCategoria())) {
            throw campoInvalido("tipologiaSrupId", "Campo não aplicável a este tipo de Instrumento.");
        }
        return listaValoresRepository.findComPaisById(tipologiaSrupId)
                .filter(valor -> TipoListaValores.TIPOLOGIA_SRUP.name().equals(valor.getTipo()) && valor.isAtivo())
                .orElseThrow(() -> campoInvalido("tipologiaSrupId", "Tipologia inexistente ou inativa."));
    }

    public List<ItemTerritorio> concelhosDisponiveis() {
        return territorioLocalService.concelhos();
    }

    public List<String> concelhos(UUID instrumentoId) {
        return instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId).stream()
                .map(InstrumentoConcelho::getMunicipioCodigo)
                .toList();
    }

    /** Os concelhos de vários Instrumentos numa só consulta; um Instrumento sem concelhos fica fora do mapa. */
    public Map<UUID, List<String>> concelhos(Collection<UUID> instrumentoIds) {
        if (instrumentoIds.isEmpty()) {
            return Map.of();
        }
        return instrumentoConcelhoRepository.findByIdInstrumentoIdInOrderByIdMunicipioCodigo(instrumentoIds).stream()
                .collect(Collectors.groupingBy(concelho -> concelho.getId().getInstrumentoId(),
                        Collectors.mapping(InstrumentoConcelho::getMunicipioCodigo, Collectors.toList())));
    }

    /**
     * Pelo menos um concelho, sem repetidos, da mesma lista que a Elaboração oferece. O âmbito do tipo diz quantos:
     * um no municipal, dois ou mais no intermunicipal (BL-PROC-067); sem âmbito definido (regionais e nacionais),
     * não há regra de número. A designação é única por tipo em cada concelho; os IGT adotados do SNIT não passam
     * por aqui.
     */
    /**
     * Os concelhos ativos da réplica do SNIT (#437). Enquanto a réplica de concelhos nunca foi sincronizada (ambiente
     * novo, antes do primeiro job das 3h, ou sem SNIT), vale a lista local das entidades CM, para a criação não ficar
     * bloqueada.
     */
    private Set<String> concelhosConhecidos(List<String> pedidos) {
        if (snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)) {
            return snitReferenciaRepository.findByListaAndCodigoIn(ListaReferenciaSnit.MUNICIPALITIES, pedidos).stream()
                    .filter(SnitReferencia::isAtivo)
                    .map(SnitReferencia::getCodigo)
                    .collect(Collectors.toSet());
        }
        log.warn("Réplica do SNIT sem concelhos: o concelho do instrumento é validado pela lista local");
        return territorioLocalService.concelhos().stream()
                .map(ItemTerritorio::codigo)
                .collect(Collectors.toSet());
    }

    private List<String> concelhosValidados(TipoInstrumento tipoInstrumento, String designacao, List<String> pedidos) {
        List<String> concelhos = pedidos == null ? List.of() : List.copyOf(pedidos);
        if (concelhos.isEmpty()) {
            throw campoInvalido("concelhos", MENSAGEM_CONCELHOS);
        }
        if (new HashSet<>(concelhos).size() != concelhos.size()) {
            throw campoInvalido("concelhos", "Concelho repetido.");
        }
        if (!concelhosConhecidos(concelhos).containsAll(concelhos)) {
            throw campoInvalido("concelhos", "Concelho inexistente.");
        }
        AmbitoTerritorial ambito = tipoInstrumento.getAmbitoTerritorial();
        if ((ambito == AmbitoTerritorial.MUNICIPAL && concelhos.size() != 1)
                || (ambito == AmbitoTerritorial.INTERMUNICIPAL && concelhos.size() < 2)) {
            throw campoInvalido("concelhos", MENSAGEM_CONCELHOS);
        }
        if (instrumentoConcelhoRepository.existeDesignacaoNosConcelhos(tipoInstrumento.getId(), designacao.strip(), concelhos)) {
            throw ReferenciaDuplicadaException.designacao("Já existe um instrumento deste tipo com esta designação no município.");
        }
        return concelhos;
    }

    private List<String> gravarConcelhos(Instrumento instrumento, List<String> concelhos) {
        instrumentoConcelhoRepository.saveAll(
                concelhos.stream().map(codigo -> new InstrumentoConcelho(instrumento, codigo)).toList());
        return concelhos;
    }

    private static ValidacaoException campoInvalido(String campo, String erro) {
        return new ValidacaoException("Payload inválido.", List.of(new ValidacaoException.CampoErro(campo, erro)));
    }

    /** Um `dtcc` fora do formato é ignorado: o IGT adota-se com os concelhos que o SNIT devolveu bem. */
    private List<String> gravarConcelhosDoIgt(Instrumento instrumento, IgtSnit igt) {
        LinkedHashSet<String> validos = new LinkedHashSet<>();
        for (String dtcc : igt.municipalities().stream().map(MunicipioIgtSnit::dtcc).filter(Objects::nonNull)
                .map(String::strip).toList()) {
            if (DTCC_VALIDO.matcher(dtcc).matches()) {
                validos.add(dtcc);
            } else {
                log.warn("dtcc do IGT do SNIT fora do formato, ignorado | idIgtSnit={} dtcc={}", igt.idIgt(), dtcc);
            }
        }
        List<String> concelhos = List.copyOf(validos);
        instrumentoConcelhoRepository.saveAll(
                concelhos.stream().map(dtcc -> new InstrumentoConcelho(instrumento, dtcc)).toList());
        return concelhos;
    }

    /** Dois pedidos em simultâneo para o mesmo IGT passam ambos a verificação prévia: decide o índice único. */
    private Instrumento guardar(Instrumento instrumento) {
        try {
            return instrumentoRepository.saveAndFlush(instrumento);
        } catch (DataIntegrityViolationException e) {
            if (instrumento.getIdIgtSnit() != null && violaUnicidadeDoIgtSnit(e)) {
                throw new ReferenciaDuplicadaException(InstrumentoSnitService.IGT_JA_EXISTE);
            }
            throw e;
        }
    }

    private static boolean violaUnicidadeDoIgtSnit(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violacao
                && RESTRICAO_IGT_SNIT_UNICO.equals(violacao.getConstraintName());
    }

    /** Os Instrumentos não têm entidade e são visíveis a todos os utilizadores autenticados. */
    @Transactional(readOnly = true)
    public Instrumento obter(UUID id) {
        return instrumentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instrumento não encontrado."));
    }

    public Page<Instrumento> listar(Pageable pageable) {
        return instrumentoRepository.findAll(pageable);
    }

    /** Os filtros da escolha dos Instrumentos a associar; cada um nulo não filtra. */
    public record FiltroInstrumentos(List<Long> tiposInstrumentoId, String concelho, Boolean emVigor, String designacao) {

        public FiltroInstrumentos {
            tiposInstrumentoId = tiposInstrumentoId == null ? List.of() : List.copyOf(tiposInstrumentoId);
        }

        boolean vazio() {
            return tiposInstrumentoId.isEmpty() && concelho == null && emVigor == null && designacao == null;
        }
    }

    /** Sem filtros, igual a {@link #listar(Pageable)}; com filtros, uma só consulta paginada (mais a contagem). */
    public Page<Instrumento> listar(FiltroInstrumentos filtro, Pageable pageable) {
        if (filtro.vazio()) {
            return listar(pageable);
        }
        return instrumentoRepository.findAll(especificacao(filtro), pageable);
    }

    private static Specification<Instrumento> especificacao(FiltroInstrumentos filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (!filtro.tiposInstrumentoId().isEmpty()) {
                predicados.add(root.get("tipoInstrumento").get("id").in(filtro.tiposInstrumentoId()));
            }
            if (filtro.emVigor() != null) {
                predicados.add(cb.equal(root.get("emVigor"), filtro.emVigor()));
            }
            if (filtro.designacao() != null) {
                // Parâmetro ligado, nunca concatenado no SQL; `%` e `_` do texto contam como literais.
                String padrao = "%" + filtro.designacao().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicados.add(cb.like(cb.lower(root.get("designacao")), padrao, '\\'));
            }
            if (filtro.concelho() != null) {
                Subquery<Integer> concelhos = query.subquery(Integer.class);
                Root<InstrumentoConcelho> concelho = concelhos.from(InstrumentoConcelho.class);
                concelhos.select(cb.literal(1)).where(
                        cb.equal(concelho.get("id").get("instrumentoId"), root.get("id")),
                        cb.equal(concelho.get("id").get("municipioCodigo"), filtro.concelho()));
                predicados.add(cb.exists(concelhos));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    /**
     * Igual para todos os atores: quem pode criar decide-o {@code verificarPossibilidadeDeCriar}. Só tipos
     * com pelo menos uma `instrumento_dinamica_config` ativa; os outros não são operáveis.
     */
    @Transactional(readOnly = true)
    public List<TipoInstrumento> listarTipos() {
        return instrumentoDinamicaConfigRepository.listarTiposInstrumentoConfigurados();
    }

    /**
     * Chamada por {@code DinamicaService} quando uma Dinâmica termina. Idempotente: não regrava nem audita se o
     * valor já é o pretendido. A ordem dos eventos manda: uma Delimitação depois de uma Revogação volta a pôr em
     * vigor. Sem `utilizadorId`, a auditoria fica como ação do sistema.
     */
    @Transactional
    public void atualizarEmVigorPorDinamica(Dinamica dinamicaTerminada, UUID utilizadorId) {
        String codigo = dinamicaTerminada.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo();
        boolean pretendido;
        if (CODIGOS_QUE_PUBLICAM.contains(codigo)) {
            pretendido = true;
        } else if (CODIGO_REVOGACAO.equals(codigo)) {
            pretendido = false;
        } else {
            return;
        }

        for (Instrumento instrumento : dinamicaTerminada.getInstrumentos()) {
            if (instrumento.isEmVigor() == pretendido) {
                continue;
            }
            boolean antes = instrumento.isEmVigor();
            instrumento.setEmVigor(pretendido);
            Instrumento guardado = instrumentoRepository.saveAndFlush(instrumento);
            Map<String, Object> dados = Map.of("emVigorAntes", antes, "emVigorDepois", pretendido,
                    "dinamicaId", dinamicaTerminada.getId());
            if (utilizadorId == null) {
                auditoriaService.registarSistema(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_EM_VIGOR, dados);
            } else {
                auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_EM_VIGOR,
                        utilizadorId, null, null, dados);
            }
        }
    }
}
