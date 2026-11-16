package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AssociarInstrumentosRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.AssociacaoInvalidaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Associação dos Instrumentos a uma Dinâmica `UM_OU_MAIS` ou `OPCIONAL` (Suspensão e Medidas Preventivas), depois do
 * formulário inicial: substitui o conjunto inteiro. Os Instrumentos são visíveis a todos, por isso não se aplica a
 * Camada 2 aos que se associam; o número exigido por `associacao_instrumentos` só se verifica na submissão.
 */
@Service
public class AssociacaoInstrumentosService {

    private static final Logger log = LoggerFactory.getLogger(AssociacaoInstrumentosService.class);

    private static final String TIPO_OCORRENCIA = "DINAMICA";
    private static final String ACAO_ASSOCIACAO = "ASSOCIACAO_INSTRUMENTOS";
    private static final String CAMPO = "instrumentoIds";
    private static final String MSG_INVALIDA = "A associação de instrumentos é inválida.";

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;

    public AssociacaoInstrumentosService(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                         InstrumentoRepository instrumentoRepository,
                                         InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                                         AutorizacaoService autorizacaoService,
                                         ContextoUtilizadorResolver contextoUtilizadorResolver,
                                         AuditoriaService auditoriaService) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Ordem: 404 fora da visibilidade → 403 perfis internos → 409 Dinâmica `UM` → 403/409 autor e estado →
     * 400 Instrumentos. Devolve a Dinâmica com os Instrumentos já carregados (open-in-view desligado).
     */
    @Transactional
    public Dinamica associar(UUID dinamicaId, AssociarInstrumentosRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Dinamica dinamica = dinamicaRepository.findById(dinamicaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dinâmica não encontrada."));
        List<Processo> processos = processoRepository.findByDinamica(dinamica);
        verificarVisibilidade(contexto, dinamica, processos);
        if (contexto.perfilInterno() != null) {
            throw new AcaoNaoAutorizadaException("Só o autor de um processo da dinâmica em submissão associa instrumentos.");
        }
        InstrumentoDinamicaConfig config = dinamica.getInstrumentoDinamicaConfig();
        if (config.getAssociacaoInstrumentos() == AssociacaoInstrumentos.UM) {
            throw new EstadoInvalidoException("Esta dinâmica tem um só instrumento, fixado ao iniciá-la.");
        }
        verificarAutorEmSubmissao(contexto, processos);

        List<Instrumento> novos = validar(dinamica, config, request.instrumentoIds());
        Set<UUID> antes = ids(dinamica.getInstrumentos());
        dinamica.substituirInstrumentos(novos);
        dinamicaRepository.saveAndFlush(dinamica);

        Set<UUID> depois = ids(dinamica.getInstrumentos());
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("associados", depois.stream().filter(id -> !antes.contains(id)).sorted().toList());
        dados.put("desassociados", antes.stream().filter(id -> !depois.contains(id)).sorted().toList());
        auditoriaService.registar(TIPO_OCORRENCIA, dinamica.getId().toString(), ACAO_ASSOCIACAO, contexto.utilizadorId(),
                contexto.entidadeId(), null, dados);
        log.info("Instrumentos da dinâmica substituídos | dinamicaId={} total={} associados={} desassociados={}",
                dinamica.getId(), depois.size(), dados.get("associados"), dados.get("desassociados"));

        dinamica.getInstrumentos().forEach(instrumento -> instrumento.getTipoInstrumento().getCodigo());
        return dinamica;
    }

    /**
     * Sem lançar, para o detalhe do processo: o chamador é o autor deste processo em `EM_SUBMISSAO`, numa Dinâmica
     * que associa Instrumentos, com as mesmas Camadas 2 e 3 de {@link #associar}.
     */
    public boolean podeAssociar(ContextoUtilizador contexto, Processo processo) {
        return contexto.perfilInterno() == null
                && processo.getDinamica().getInstrumentoDinamicaConfig().getAssociacaoInstrumentos() != AssociacaoInstrumentos.UM
                && processo.getEstado() == EstadoProcesso.EM_SUBMISSAO
                && autorizacaoService.podeAgirComoAutor(contexto, processo);
    }

    /** Os tipos de Instrumento que a Dinâmica admite; vazia nas Dinâmicas `UM`, que não associam por aqui. */
    public List<TipoInstrumento> tiposAdmitidos(Dinamica dinamica) {
        InstrumentoDinamicaConfig config = dinamica.getInstrumentoDinamicaConfig();
        return config.getAssociacaoInstrumentos() == AssociacaoInstrumentos.UM ? List.of()
                : instrumentoDinamicaConfigRepository.listarTiposInstrumentoAdmitidos(config.getTipoDinamica().getId());
    }

    /** Quem a iniciou, os perfis com visibilidade transversal, ou quem vê algum dos processos dela. */
    private void verificarVisibilidade(ContextoUtilizador contexto, Dinamica dinamica, List<Processo> processos) {
        boolean visivel = contexto.temVisibilidadeTransversal()
                || dinamica.getCriadoPor().getId().equals(contexto.utilizadorId())
                || processos.stream().anyMatch(processo -> autorizacaoService.processoVisivel(contexto, processo));
        if (!visivel) {
            throw new RecursoNaoEncontradoException("Dinâmica não encontrada.");
        }
    }

    /** Um processo em submissão de outro utilizador que a sessão vê dá 403; sem nenhum, 409. */
    private void verificarAutorEmSubmissao(ContextoUtilizador contexto, List<Processo> processos) {
        List<Processo> emSubmissao = processos.stream()
                .filter(processo -> processo.getEstado() == EstadoProcesso.EM_SUBMISSAO).toList();
        Processo doChamador = emSubmissao.stream()
                .filter(processo -> processo.getCriadoPor().getId().equals(contexto.utilizadorId()))
                .findFirst().orElse(null);
        if (doChamador == null) {
            if (emSubmissao.stream().anyMatch(processo -> autorizacaoService.processoVisivel(contexto, processo))) {
                throw new AcaoNaoAutorizadaException("Só o autor do processo em submissão associa instrumentos.");
            }
            throw new EstadoInvalidoException("A dinâmica não tem nenhum processo seu em submissão.");
        }
        autorizacaoService.verificarPermissaoDeAgir(contexto, doChamador);
    }

    private List<Instrumento> validar(Dinamica dinamica, InstrumentoDinamicaConfig config, List<UUID> instrumentoIds) {
        Map<UUID, Instrumento> encontrados = instrumentoRepository.findAllById(new HashSet<>(instrumentoIds)).stream()
                .collect(Collectors.toMap(Instrumento::getId, Function.identity()));
        Set<Long> tiposAdmitidos = instrumentoDinamicaConfigRepository
                .listarTiposInstrumentoAdmitidos(config.getTipoDinamica().getId()).stream()
                .map(TipoInstrumento::getId).collect(Collectors.toSet());

        List<CampoErro> erros = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (int i = 0; i < instrumentoIds.size(); i++) {
            UUID id = instrumentoIds.get(i);
            Instrumento instrumento = encontrados.get(id);
            String erro;
            if (!vistos.add(id)) {
                erro = "O instrumento está repetido no conjunto.";
            } else if (instrumento == null) {
                erro = "Instrumento não encontrado.";
            } else if (!instrumento.isEmVigor()) {
                erro = "Só se associam instrumentos em vigor.";
            } else if (!tiposAdmitidos.contains(instrumento.getTipoInstrumento().getId())) {
                erro = "Esta dinâmica não admite instrumentos do tipo " + instrumento.getTipoInstrumento().getCodigo() + ".";
            } else {
                continue;
            }
            erros.add(new CampoErro(CAMPO + "[" + i + "]", erro));
        }
        // O Instrumento de início dá os dados do Instrumento ao processo: fica sempre no conjunto.
        dinamica.instrumentoDeInicio().filter(inicio -> !vistos.contains(inicio.getId()))
                .ifPresent(inicio -> erros.add(new CampoErro(CAMPO,
                        "O instrumento a partir do qual a dinâmica foi iniciada não se desassocia.")));
        if (!erros.isEmpty()) {
            throw new AssociacaoInvalidaException(erros.size() == 1 ? erros.get(0).erro() : MSG_INVALIDA, erros);
        }
        return instrumentoIds.stream().distinct().map(encontrados::get).toList();
    }

    private static Set<UUID> ids(Set<Instrumento> instrumentos) {
        return instrumentos.stream().map(Instrumento::getId).collect(Collectors.toSet());
    }
}
