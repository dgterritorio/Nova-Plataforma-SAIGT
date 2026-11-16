package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.dto.instrumento.CriarElaboracaoRequest;
import com.conkord.saigt.dto.instrumento.CriarInstrumentoRequest;
import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.dto.instrumento.ElaboracaoNovaResponse;
import com.conkord.saigt.dto.instrumento.ElaboracaoNovaResponse.ConcelhoElaboracao;
import com.conkord.saigt.dto.instrumento.ProcedimentoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ElaboracaoInexistenteException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PedeLocalizacao;
import com.conkord.saigt.persistence.configuracao.PerguntaGeoconform;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * IGT novo de um tipo com Elaboração: o assistente vai direto para o formulário, e o primeiro «Guardar» cria o
 * Instrumento, a Dinâmica e a primeira tentativa numa só transação. Os outros caminhos não passam por aqui.
 */
@Service
public class ElaboracaoService {

    static final String CODIGO_ELABORACAO = "ELABORACAO";
    static final int TAMANHO_MAXIMO_DESCRITIVO = 50;
    static final String CONCELHO_FORA_DA_NUT_II = "O concelho não pertence à NUT II escolhida.";
    private static final Logger log = LoggerFactory.getLogger(ElaboracaoService.class);
    // Em Java e não no ORDER BY: a collation da base não ordena os acentos à portuguesa.
    private static final Collator COLLATOR_PT = Collator.getInstance(Locale.forLanguageTag("pt-PT"));

    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final EntidadeRepository entidadeRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final InstrumentoDisponibilidadeService instrumentoDisponibilidadeService;
    private final TerritorioLocalService territorioLocalService;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;
    private final InstrumentoService instrumentoService;
    private final DinamicaService dinamicaService;
    private final ProcessoService processoService;

    public ElaboracaoService(TipoInstrumentoRepository tipoInstrumentoRepository,
                             InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                             InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                             EntidadeRepository entidadeRepository,
                             AutorizacaoService autorizacaoService, ContextoUtilizadorResolver contextoUtilizadorResolver,
                             InstrumentoDisponibilidadeService instrumentoDisponibilidadeService,
                             TerritorioLocalService territorioLocalService,
                             ReferenciaSnitConsultaService referenciaSnitConsultaService,
                             InstrumentoService instrumentoService,
                             DinamicaService dinamicaService, ProcessoService processoService) {
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.entidadeRepository = entidadeRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.instrumentoDisponibilidadeService = instrumentoDisponibilidadeService;
        this.territorioLocalService = territorioLocalService;
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
        this.instrumentoService = instrumentoService;
        this.dinamicaService = dinamicaService;
        this.processoService = processoService;
    }

    public record ElaboracaoCriada(Instrumento instrumento, Dinamica dinamica, Processo processo) {
    }

    /** 404 `ELABORACAO_INEXISTENTE` quando o tipo não tem Elaboração: o ecrã segue então o caminho antigo. */
    @Transactional(readOnly = true)
    public ElaboracaoNovaResponse elaboracaoNova(Long tipoInstrumentoId) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.findById(tipoInstrumentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Instrumento não encontrado."));
        autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento);

        InstrumentoDinamicaConfig elaboracao = instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(tipoInstrumento)
                .stream()
                .filter(config -> CODIGO_ELABORACAO.equals(config.getTipoDinamica().getCodigo()))
                .findFirst()
                .orElseThrow(ElaboracaoInexistenteException::new);
        ProcedimentoDisponivelResponse procedimento = instrumentoDisponibilidadeService.procedimentoInicial(elaboracao)
                .orElseThrow(() -> new AcaoNaoAutorizadaException("Sem permissão para iniciar a Elaboração deste tipo de IGT."));

        Optional<InstrumentoProcedimentoConfig> config =
                instrumentoProcedimentoConfigRepository.findById(procedimento.instrumentoProcedimentoConfigId());
        // Sem `elementosAfetados` ainda, só a pergunta obrigatória se sabe antes de gravar.
        boolean exigeOpcaoGeoconform = config
                .map(c -> c.getPerguntaGeoconform() == PerguntaGeoconform.OBRIGATORIA)
                .orElse(false);
        String pedeLocalizacao = config.map(c -> c.getPedeLocalizacao().name()).orElse(PedeLocalizacao.NAO.name());
        List<ConcelhoElaboracao> concelhos = concelhosDaReplica();
        if (concelhos.isEmpty()) {
            log.warn("Réplica do SNIT sem concelhos: o formulário do IGT novo fica sem NUT II nem concelhos");
        }
        return new ElaboracaoNovaResponse(elaboracao.getId(), elaboracao.getTipoDinamica().getNome(), procedimento,
                exigeOpcaoGeoconform, pedeLocalizacao, nutsIIDaReplica(), concelhos,
                concelhoDaEntidade(contexto), tipoInstrumento.getAmbitoTerritorial());
    }

    private List<ItemTerritorio> nutsIIDaReplica() {
        return referenciaSnitConsultaService.nutsII().stream()
                .map(nutII -> new ItemTerritorio(nutII.codigo(), nutII.nome()))
                .toList();
    }

    /** Códigos e NUT II da réplica; o nome é o curado das câmaras, porque o do SNIT repete «CALHETA». */
    private List<ConcelhoElaboracao> concelhosDaReplica() {
        Map<String, String> nomesCurados = territorioLocalService.concelhos().stream()
                .collect(Collectors.toMap(ItemTerritorio::codigo, ItemTerritorio::nome, (primeiro, outro) -> primeiro));
        return referenciaSnitConsultaService.concelhos(null).stream()
                .map(concelho -> new ConcelhoElaboracao(concelho.codigo(),
                        nomesCurados.getOrDefault(concelho.codigo(), concelho.nome()), concelho.nut2()))
                .sorted(Comparator.comparing(ConcelhoElaboracao::nome, COLLATOR_PT).thenComparing(ConcelhoElaboracao::codigo))
                .toList();
    }

    @Transactional
    public ElaboracaoCriada criar(CriarElaboracaoRequest request) {
        ElaboracaoNovaResponse elaboracao = elaboracaoNova(request.tipoInstrumentoId());
        validar(request, elaboracao);

        String designacao = request.designacao().strip();
        Instrumento instrumento = instrumentoService.criar(
                new CriarInstrumentoRequest(request.tipoInstrumentoId(), designacao, request.concelhosPedidos()));

        Dinamica dinamica = dinamicaService.iniciar(instrumento.getId(),
                new CriarDinamicaRequest(elaboracao.instrumentoDinamicaConfigId(), descritivo(designacao)));
        Processo processo = processoService.criar(dinamica.getId(),
                new CriarProcessoRequest(elaboracao.procedimento().instrumentoProcedimentoConfigId(), request.tipoAtoId()));
        return new ElaboracaoCriada(instrumento, dinamica, processo);
    }

    static String descritivo(String designacao) {
        return designacao.length() <= TAMANHO_MAXIMO_DESCRITIVO
                ? designacao
                : designacao.substring(0, TAMANHO_MAXIMO_DESCRITIVO).strip();
    }

    private static void validar(CriarElaboracaoRequest request, ElaboracaoNovaResponse elaboracao) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        if (request.nutIICodigo() != null && !request.nutIICodigo().isBlank()
                && naoExiste(elaboracao.nutsII(), request.nutIICodigo())) {
            erros.add(new ValidacaoException.CampoErro("nutIICodigo", "NUT II inexistente."));
        } else if (concelhoForaDaNutII(request, elaboracao)) {
            erros.add(new ValidacaoException.CampoErro("concelho", CONCELHO_FORA_DA_NUT_II));
        }
        boolean tipoAtoDisponivel = elaboracao.procedimento().tiposAtoDisponiveis().stream()
                .anyMatch(tipoAto -> Objects.equals(tipoAto.id(), request.tipoAtoId()));
        if (!tipoAtoDisponivel) {
            erros.add(new ValidacaoException.CampoErro("tipoAtoId", "Tipo de ato não disponível para este procedimento."));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Payload inválido.", erros);
        }
    }

    /** Só nos municipais (EF: concelho «em função de NUTS»), e só com a NUT II do concelho conhecida. */
    private static boolean concelhoForaDaNutII(CriarElaboracaoRequest request, ElaboracaoNovaResponse elaboracao) {
        List<String> pedidos = request.concelhosPedidos();
        if (elaboracao.ambitoTerritorial() != AmbitoTerritorial.MUNICIPAL || pedidos == null
                || request.nutIICodigo() == null || request.nutIICodigo().isBlank()) {
            return false;
        }
        return elaboracao.concelhos().stream()
                .filter(concelho -> pedidos.contains(concelho.codigo()) && concelho.nutIICodigo() != null)
                .anyMatch(concelho -> !concelho.nutIICodigo().equals(request.nutIICodigo()));
    }

    private static boolean naoExiste(List<ItemTerritorio> itens, String codigo) {
        return itens.stream().noneMatch(item -> item.codigo().equals(codigo));
    }

    private String concelhoDaEntidade(ContextoUtilizador contexto) {
        if (contexto.entidadeId() == null) {
            return null;
        }
        return entidadeRepository.findById(contexto.entidadeId()).stream()
                .filter(entidade -> entidade.getTipoEntidade() == TipoEntidade.CM)
                .map(Entidade::getDico)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
