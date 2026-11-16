package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.ClassificacaoSrupInstrumento;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse.DinamicaPainel;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse.ProcedimentoPainel;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse.ProcessoPainel;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse.Semaforo;
import com.conkord.saigt.dto.instrumento.InstrumentoReferenciaResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * O Instrumento e as Dinâmicas são consultáveis por qualquer utilizador autenticado; dos processos só se listam os
 * visíveis pela Camada 2. Construído dentro da transação de leitura: navega associações LAZY.
 */
@Service
public class InstrumentoPainelService {

    private static final String CODIGO_REVISAO = "REVISAO";

    private final InstrumentoRepository instrumentoRepository;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;

    public InstrumentoPainelService(InstrumentoRepository instrumentoRepository,
                                    InstrumentoConcelhoRepository instrumentoConcelhoRepository, DinamicaRepository dinamicaRepository,
                                    ProcessoRepository processoRepository,
                                    InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                                    InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                    AutorizacaoService autorizacaoService,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver,
                                    ReferenciaSnitConsultaService referenciaSnitConsultaService) {
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
        this.instrumentoRepository = instrumentoRepository;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public InstrumentoPainelResponse painel(UUID instrumentoId) {
        Instrumento instrumento = instrumentoRepository.findById(instrumentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instrumento não encontrado."));
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();

        List<Dinamica> dinamicas = dinamicaRepository.listarQueAbrangem(instrumentoId);
        Map<UUID, List<Processo>> processosPorDinamica = dinamicas.isEmpty()
                ? Map.of()
                : processoRepository.findByDinamicaInOrderByOcorrenciaAsc(dinamicas).stream()
                        .filter(processo -> autorizacaoService.processoVisivel(contexto, processo))
                        .collect(Collectors.groupingBy(processo -> processo.getDinamica().getId()));
        Instant fimDoCicloAnterior = fimDoCicloAnterior(dinamicas);
        Map<Long, List<InstrumentoProcedimentoConfig>> procedimentosPorConfig = new HashMap<>();

        List<DinamicaPainel> painel = new ArrayList<>();
        for (Dinamica dinamica : dinamicas) {
            List<InstrumentoProcedimentoConfig> configurados = procedimentosConfigurados(
                    dinamica.getInstrumentoDinamicaConfig(), procedimentosPorConfig);
            painel.add(iniciada(dinamica, configurados,
                    processosPorDinamica.getOrDefault(dinamica.getId(), List.of()), fimDoCicloAnterior));
        }

        Set<Long> configsIniciadas = dinamicas.stream()
                .map(dinamica -> dinamica.getInstrumentoDinamicaConfig().getId())
                .collect(Collectors.toSet());
        instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(instrumento.getTipoInstrumento()).stream()
                .filter(config -> !configsIniciadas.contains(config.getId()))
                .sorted(Comparator.comparing(config -> config.getTipoDinamica().getId()))
                .map(config -> naoIniciada(config, procedimentosConfigurados(config, procedimentosPorConfig)))
                .forEach(painel::add);

        List<String> concelhos = instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId)
                .stream().map(InstrumentoConcelho::getMunicipioCodigo).toList();
        return new InstrumentoPainelResponse(instrumento.getId(), instrumento.getTipoInstrumento().getId(),
                instrumento.getDesignacao(), instrumento.getNumero(), instrumento.getIdIgtSnit(), concelhos,
                referenciaSnitConsultaService.nomesConcelhosPorOrdem(concelhos), instrumento.isEmVigor(), instrumento.getOrigemDados(),
                instrumento.getCriadoPor().getId(), instrumento.getCriadoEm(),
                ClassificacaoSrupInstrumento.de(instrumento),
                InstrumentoReferenciaResponse.de(instrumento.getInstrumentoOrigem()), painel);
    }

    /**
     * Uma Revisão terminada fecha o ciclo anterior: as Dinâmicas terminadas antes dela passam a históricas. Nulo
     * enquanto nenhuma Revisão terminou.
     */
    private static Instant fimDoCicloAnterior(List<Dinamica> dinamicas) {
        return dinamicas.stream()
                .filter(dinamica -> dinamica.getEstado() == EstadoDinamica.TERMINADA && dinamica.getDataConclusao() != null)
                .filter(dinamica -> CODIGO_REVISAO.equals(dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo()))
                .map(Dinamica::getDataConclusao)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean historica(Dinamica dinamica, Instant fimDoCicloAnterior) {
        return fimDoCicloAnterior != null
                && dinamica.getEstado() == EstadoDinamica.TERMINADA
                && dinamica.getDataConclusao() != null
                && dinamica.getDataConclusao().isBefore(fimDoCicloAnterior);
    }

    private static Semaforo semaforo(EstadoDinamica estado) {
        return switch (estado) {
            case TERMINADA -> Semaforo.VERDE;
            case EM_CURSO -> Semaforo.AMARELO;
            case CADUCADA -> Semaforo.VERMELHO;
        };
    }

    private List<InstrumentoProcedimentoConfig> procedimentosConfigurados(
            InstrumentoDinamicaConfig config, Map<Long, List<InstrumentoProcedimentoConfig>> cache) {
        return cache.computeIfAbsent(config.getId(),
                id -> instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(config));
    }

    private static DinamicaPainel iniciada(Dinamica dinamica, List<InstrumentoProcedimentoConfig> configurados,
                                           List<Processo> processos, Instant fimDoCicloAnterior) {
        // Um Procedimento entretanto desativado continua a aparecer enquanto tiver processos.
        Map<Long, InstrumentoProcedimentoConfig> procedimentos = new LinkedHashMap<>();
        configurados.forEach(config -> procedimentos.put(config.getId(), config));
        processos.forEach(processo -> procedimentos.putIfAbsent(processo.getInstrumentoProcedimentoConfig().getId(),
                processo.getInstrumentoProcedimentoConfig()));
        Map<Long, List<Processo>> processosPorProcedimento = processos.stream()
                .collect(Collectors.groupingBy(processo -> processo.getInstrumentoProcedimentoConfig().getId()));

        List<ProcedimentoPainel> procedimentosPainel = procedimentos.values().stream()
                .sorted(Comparator.comparingInt(InstrumentoProcedimentoConfig::getOrdem))
                .map(config -> procedimento(config, processosPorProcedimento.getOrDefault(config.getId(), List.of())))
                .toList();

        return new DinamicaPainel(dinamica.getId(), dinamica.getNumeroDinamica(), dinamica.getDescritivo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(), dinamica.getEstado().name(),
                dinamica.getTentativa(), semaforo(dinamica.getEstado()), dinamica.getDataInicio(),
                dinamica.getDataConclusao(), dinamica.getDataCaducidade(), historica(dinamica, fimDoCicloAnterior),
                procedimentosPainel);
    }

    private static DinamicaPainel naoIniciada(InstrumentoDinamicaConfig config,
                                              List<InstrumentoProcedimentoConfig> configurados) {
        List<ProcedimentoPainel> procedimentos = configurados.stream()
                .map(procedimento -> procedimento(procedimento, List.of()))
                .toList();
        return new DinamicaPainel(null, null, null, config.getTipoDinamica().getCodigo(), config.getTipoDinamica().getNome(), null, null,
                Semaforo.CINZENTO, null, null, null, false, procedimentos);
    }

    private static ProcedimentoPainel procedimento(InstrumentoProcedimentoConfig config, List<Processo> processos) {
        return new ProcedimentoPainel(config.getId(), config.getTipoProcedimento().getCodigo(),
                config.getTipoProcedimento().getNome(), config.getOrdem(),
                config.isObrigatorio(), config.isAtoPontual(), processos.stream().map(InstrumentoPainelService::processo).toList());
    }

    private static ProcessoPainel processo(Processo processo) {
        Processo referido = processo.getProcessoReferido();
        return new ProcessoPainel(processo.getId(), processo.getNumeroProcesso(), processo.getOcorrencia(),
                processo.getEstado().name(), processo.getNumeroDr(), processo.getDataPublicacaoDr(), processo.getLinkDr(),
                processo.getDestino().name(), referido == null ? null : referido.getId(),
                referido == null ? null : referido.getNumeroProcesso());
    }
}
