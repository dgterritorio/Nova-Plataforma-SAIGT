package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.dto.instrumento.IniciarDinamicaRequest;
import com.conkord.saigt.exception.DinamicaNaoElegivelException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.MotorRegrasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A elegibilidade de início é decidida por {@code AutorizacaoService#verificarPossibilidadeDeIniciarDinamica},
 * com o mesmo motor de regras do endpoint de disponibilidade. Ocorrências repetidas do mesmo tipo só
 * são bloqueadas pelo motor de regras (ex.: `OCORRENCIA_NAO_EXISTE`).
 */
@Service
public class DinamicaService {

    private static final Logger log = LoggerFactory.getLogger(DinamicaService.class);

    private static final String TIPO_OCORRENCIA = "DINAMICA";
    private static final String ACAO_CRIACAO = "CRIACAO";
    private static final String ACAO_CONCLUSAO_AUTOMATICA = "CONCLUSAO_AUTOMATICA";

    private final InstrumentoService instrumentoService;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;
    private final MotorRegrasService motorRegrasService;
    private final TipoDinamicaRepository tipoDinamicaRepository;

    public DinamicaService(InstrumentoService instrumentoService,
                            InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                            InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                            DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                            UtilizadorRepository utilizadorRepository, AutorizacaoService autorizacaoService,
                            ContextoUtilizadorResolver contextoUtilizadorResolver, AuditoriaService auditoriaService,
                            MotorRegrasService motorRegrasService, TipoDinamicaRepository tipoDinamicaRepository) {
        this.instrumentoService = instrumentoService;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
        this.motorRegrasService = motorRegrasService;
        this.tipoDinamicaRepository = tipoDinamicaRepository;
    }

    /**
     * `estado`/`origemDados`/`tentativa` vêm dos defaults do campo. {@code saveAndFlush}: `dataInicio`
     * ({@code @CreationTimestamp}) só é populado após o flush.
     */
    @Transactional
    public Dinamica iniciar(UUID instrumentoId, CriarDinamicaRequest request) {
        Instrumento instrumento = instrumentoService.obter(instrumentoId); // 404 se fora de visibilidade
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();

        InstrumentoDinamicaConfig config = instrumentoDinamicaConfigRepository.findById(request.instrumentoDinamicaConfigId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Dinâmica não encontrada."));

        autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config);

        Utilizador utilizador = utilizadorRepository.findById(contexto.utilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));

        Dinamica dinamica = dinamicaRepository.saveAndFlush(
                new Dinamica(instrumento, config, request.descritivo().strip(), utilizador));

        auditoriaService.registar(TIPO_OCORRENCIA, dinamica.getId().toString(), ACAO_CRIACAO,
                utilizador.getId(), contexto.entidadeId(), null, null);

        // Força a inicialização LAZY de tipoDinamica e dos Instrumentos abrangidos agora — o DinamicaResponse é
        // montado no controller, já fora desta sessão Hibernate (open-in-view desligado).
        config.getTipoDinamica().getCodigo();
        dinamica.getInstrumentos().size();

        return dinamica;
    }

    /**
     * Início sem IGT de partida (API-PROC-004): só nas configurações de tipos com `inicio_sem_igt`. O motor avalia só as
     * regras que não dependem de um Instrumento; a cardinalidade por `associacao_instrumentos` e as regras de
     * Instrumento verificam-se na associação e na submissão. Grava a Dinâmica sem linhas em `dinamica_instrumento`.
     */
    @Transactional
    public Dinamica iniciarSemIgt(IniciarDinamicaRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        InstrumentoDinamicaConfig config = instrumentoDinamicaConfigRepository.findById(request.instrumentoDinamicaConfigId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Dinâmica não encontrada."));
        TipoDinamica tipoDinamica = config.getTipoDinamica();
        if (!tipoDinamica.isInicioSemIgt()) {
            throw new DinamicaNaoElegivelException("Esta dinâmica inicia-se a partir do IGT.");
        }
        if (!config.isAtivo() || !tipoDinamica.isAtivo() || !config.getTipoInstrumento().isAtivo()) {
            throw new DinamicaNaoElegivelException("Dinâmica indisponível — configuração inativa.");
        }

        autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, config);

        if (!motorRegrasService.avaliarElegibilidadeDinamicaSemInstrumento(config)) {
            throw new DinamicaNaoElegivelException("Dinâmica indisponível — regra de elegibilidade não cumprida.");
        }

        String descritivo = request.descritivo().strip();
        if (tipoDinamica.isDesignacaoUnica()) {
            // Bloqueia o tipo até ao fim da transação: dois pedidos simultâneos não gravam a mesma designação.
            tipoDinamicaRepository.bloquear(tipoDinamica.getId());
            if (dinamicaRepository.existeDoTipoComDescritivo(tipoDinamica.getId(), descritivo)) {
                throw ReferenciaDuplicadaException.designacao("Já existe uma dinâmica deste tipo com esta designação.",
                        "descritivo");
            }
        }

        Utilizador utilizador = utilizadorRepository.findById(contexto.utilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(config, descritivo, utilizador));

        auditoriaService.registar(TIPO_OCORRENCIA, dinamica.getId().toString(), ACAO_CRIACAO,
                utilizador.getId(), contexto.entidadeId(), null, null);
        log.info("Dinâmica iniciada sem IGT | dinamicaId={} dinamicaConfigId={} entidadeId={}", dinamica.getId(),
                config.getId(), contexto.entidadeId());

        tipoDinamica.getCodigo();
        dinamica.getInstrumentos().size();
        return dinamica;
    }

    /**
     * Chamado quando um `processo` atinge `PUBLICADO`. A Dinâmica termina quando o Procedimento final está
     * publicado, mesmo fora de sequência ou depois de caducar. Corre na transação de quem chama, para
     * `dinamica`/`instrumento` não ficarem inconsistentes com `processo`.
     */
    @Transactional
    public void verificarConclusaoAutomatica(Dinamica dinamica) {
        terminarSeProcedimentoFinalPublicado(dinamica).ifPresent(origem -> {
            ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
            auditoriaService.registarTransicao(TIPO_OCORRENCIA, dinamica.getId().toString(), ACAO_CONCLUSAO_AUTOMATICA,
                    TransicaoEstado.de(origem, EstadoDinamica.TERMINADA), contexto.utilizadorId(),
                    contexto.entidadeId(), null);
            instrumentoService.atualizarEmVigorPorDinamica(dinamica, contexto.utilizadorId());
        });
    }

    /** Igual a {@link #verificarConclusaoAutomatica}, quando a publicação chega da INCM sem utilizador na sessão. */
    @Transactional
    public void verificarConclusaoPeloSistema(Dinamica dinamica) {
        terminarSeProcedimentoFinalPublicado(dinamica).ifPresent(origem -> {
            auditoriaService.registarSistema(TIPO_OCORRENCIA, dinamica.getId().toString(), ACAO_CONCLUSAO_AUTOMATICA,
                    TransicaoEstado.de(origem, EstadoDinamica.TERMINADA), null);
            instrumentoService.atualizarEmVigorPorDinamica(dinamica, null);
        });
    }

    /** O estado de que a Dinâmica saiu, ou vazio se não terminou (já `TERMINADA`, sem Procedimento final ou por publicar). */
    private Optional<EstadoDinamica> terminarSeProcedimentoFinalPublicado(Dinamica dinamica) {
        EstadoDinamica origem = dinamica.getEstado();
        if (origem == EstadoDinamica.TERMINADA) {
            return Optional.empty();
        }
        Optional<InstrumentoProcedimentoConfig> procedimentoFinal = instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc(
                        dinamica.getInstrumentoDinamicaConfig());
        if (procedimentoFinal.isEmpty() || !processoRepository.existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(
                dinamica, procedimentoFinal.get(), EstadoProcesso.PUBLICADO)) {
            return Optional.empty();
        }
        dinamica.setEstado(EstadoDinamica.TERMINADA);
        dinamica.setDataConclusao(Instant.now());
        dinamicaRepository.saveAndFlush(dinamica);
        return Optional.of(origem);
    }
}
