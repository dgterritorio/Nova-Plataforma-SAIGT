package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.DinamicaDisponivelResponse;
import com.conkord.saigt.dto.instrumento.DinamicaEmCursoResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoDisponibilidadeResponse;
import com.conkord.saigt.dto.instrumento.ProcedimentoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.UltimaDinamicaPublicadaResponse;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.MotorRegrasService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O frontend nunca conhece a estrutura do processo, só renderiza esta resposta. As regras vêm sempre de
 * {@code AutorizacaoService}/{@code MotorRegrasService}, nunca reimplementadas aqui; os Procedimentos disponíveis de
 * cada Dinâmica em curso vêm de {@link ProcedimentosDisponiveisService}, partilhado com `GET /api/v1/dinamicas/disponivel`.
 */
@Service
public class InstrumentoDisponibilidadeService {

    private static final List<EstadoDinamica> ESTADOS_EM_ABERTO = List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA);

    private final InstrumentoService instrumentoService;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final DinamicaRepository dinamicaRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final MotorRegrasService motorRegrasService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ProcedimentosDisponiveisService procedimentosDisponiveisService;

    public InstrumentoDisponibilidadeService(InstrumentoService instrumentoService,
                                              InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                                              DinamicaRepository dinamicaRepository,
                                              InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                              MotorRegrasService motorRegrasService,
                                              ContextoUtilizadorResolver contextoUtilizadorResolver,
                                              ProcedimentosDisponiveisService procedimentosDisponiveisService) {
        this.instrumentoService = instrumentoService;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.motorRegrasService = motorRegrasService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.procedimentosDisponiveisService = procedimentosDisponiveisService;
    }

    /** {@code @Transactional}: mantém a sessão aberta para as leituras LAZY Instrumento → Dinâmica → Procedimento. */
    @Transactional(readOnly = true)
    public InstrumentoDisponibilidadeResponse disponivel(UUID instrumentoId) {
        Instrumento instrumento = instrumentoService.obter(instrumentoId); // 404 se fora de visibilidade.
        ProcedimentosDisponiveisService.Sessao sessao = procedimentosDisponiveisService.sessao(contextoUtilizadorResolver.resolver());

        List<DinamicaDisponivelResponse> dinamicasDisponiveis = instrumentoDinamicaConfigRepository
                .findByTipoInstrumentoAndAtivoTrue(instrumento.getTipoInstrumento()).stream()
                // Só se inicia a Dinâmica pelo Procedimento inicial: a linha mais adiante não basta.
                .filter(config -> instrumentoProcedimentoConfigRepository
                        .findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(config)
                        .map(inicial -> sessao.configsAutorizados().contains(inicial.getId())).orElse(false))
                .filter(config -> motorRegrasService.avaliarElegibilidadeDinamica(config, instrumentoId))
                .map(config -> new DinamicaDisponivelResponse(config.getId(), config.getTipoDinamica().getCodigo(),
                        config.getTipoDinamica().getNome()))
                .toList();

        // Uma Dinâmica caducada continua em aberto: a caducidade nunca bloqueia. Só as iniciadas a partir deste
        // Instrumento: as iniciadas sem IGT de partida estão em `GET /api/v1/dinamicas/disponivel`.
        List<DinamicaEmCursoResponse> dinamicasEmCurso = new ArrayList<>(dinamicaRepository
                .findByInstrumento_IdAndEstadoIn(instrumentoId, ESTADOS_EM_ABERTO).stream()
                .map(dinamica -> mapearDinamicaEmCurso(dinamica, procedimentosDisponiveisService.deDinamica(dinamica, sessao)))
                .toList());
        // A lista é a das Dinâmicas onde se pode continuar: também as terminadas com a Prorrogação por fazer.
        List<Dinamica> terminadas = dinamicaRepository.listarTerminadasComProcedimentosPosterioresAoFinal(instrumentoId);
        Map<UUID, List<ProcedimentoDisponivelResponse>> daTerminadas = procedimentosDisponiveisService.deDinamicasTerminadas(terminadas, sessao);
        terminadas.stream()
                .filter(dinamica -> daTerminadas.containsKey(dinamica.getId()))
                .map(dinamica -> mapearDinamicaEmCurso(dinamica, daTerminadas.get(dinamica.getId())))
                .forEach(dinamicasEmCurso::add);

        // Pelas Dinâmicas que abrangem o Instrumento, como o painel.
        UltimaDinamicaPublicadaResponse ultimaDinamicaPublicada = dinamicaRepository
                .listarAbrangendoComEstadoPelaConclusaoMaisRecente(instrumentoId, EstadoDinamica.TERMINADA, Limit.of(1))
                .stream().findFirst()
                .map(dinamica -> new UltimaDinamicaPublicadaResponse(dinamica.getId(),
                        dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                        dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(), dinamica.getDataConclusao()))
                .orElse(null);

        return new InstrumentoDisponibilidadeResponse(dinamicasDisponiveis, dinamicasEmCurso, ultimaDinamicaPublicada);
    }

    /**
     * O primeiro Procedimento (menor `ordem`) de uma Dinâmica ainda por iniciar, se o tipo da entidade o pode fazer; com os
     * mesmos documentos e tipos de ato que `disponivel` daria.
     */
    @Transactional(readOnly = true)
    public Optional<ProcedimentoDisponivelResponse> procedimentoInicial(InstrumentoDinamicaConfig dinamicaConfig) {
        return procedimentosDisponiveisService.procedimentoInicial(dinamicaConfig,
                procedimentosDisponiveisService.sessao(contextoUtilizadorResolver.resolver()));
    }

    private static DinamicaEmCursoResponse mapearDinamicaEmCurso(Dinamica dinamica,
                                                                 List<ProcedimentoDisponivelResponse> procedimentosDisponiveis) {
        return new DinamicaEmCursoResponse(dinamica.getId(), dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(),
                dinamica.getDescritivo(), dinamica.getEstado().name(), dinamica.getDataInicio(), dinamica.getDataCaducidade(),
                procedimentosDisponiveis);
    }
}
