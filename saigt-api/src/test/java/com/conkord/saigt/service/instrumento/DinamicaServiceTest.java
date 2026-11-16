package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DinamicaServiceTest {

    private final InstrumentoService instrumentoService = mock(InstrumentoService.class);
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository = mock(InstrumentoDinamicaConfigRepository.class);
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository = mock(InstrumentoProcedimentoConfigRepository.class);
    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final MotorRegrasService motorRegrasService = mock(MotorRegrasService.class);
    private final TipoDinamicaRepository tipoDinamicaRepository = mock(TipoDinamicaRepository.class);

    private final DinamicaService service = new DinamicaService(instrumentoService, instrumentoDinamicaConfigRepository,
            instrumentoProcedimentoConfigRepository, dinamicaRepository, processoRepository, utilizadorRepository,
            autorizacaoService, contextoUtilizadorResolver, auditoriaService, motorRegrasService, tipoDinamicaRepository);

    private UUID instrumentoId;
    private Instrumento instrumento;
    private Entidade entidade;
    private InstrumentoDinamicaConfig config;
    private UUID utilizadorId;
    private Utilizador utilizador;
    private ContextoUtilizador contexto;
    private CriarDinamicaRequest request;

    @BeforeEach
    void setUp() {
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        instrumentoId = UUID.randomUUID();
        instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", instrumentoId);
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", pdm);

        config = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("ELABORACAO", "Elaboração"));
        ReflectionTestUtils.setField(config, "id", 5L);

        utilizadorId = UUID.randomUUID();
        utilizador = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        ReflectionTestUtils.setField(utilizador, "id", utilizadorId);

        contexto = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), "ENTIDADE", null, null, null, null, TipoEntidade.CM, entidade.getId());
        request = new CriarDinamicaRequest(5L, "Alteração do PDM");

        when(instrumentoService.obter(instrumentoId)).thenReturn(instrumento);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(instrumentoDinamicaConfigRepository.findById(5L)).thenReturn(Optional.of(config));
        when(utilizadorRepository.findById(utilizadorId)).thenReturn(Optional.of(utilizador));
        when(dinamicaRepository.saveAndFlush(any(Dinamica.class))).thenAnswer(invocation -> {
            Dinamica dinamica = invocation.getArgument(0);
            ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
            return dinamica;
        });
    }

    @Test
    void deveCriarDinamicaEmCursoENativaQuandoElegivel() {
        Dinamica dinamica = service.iniciar(instrumentoId, request);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(dinamica.getOrigemDados()).isEqualTo("NATIVO");
        assertThat(dinamica.getInstrumento()).isEqualTo(instrumento);
        assertThat(dinamica.getInstrumentoDinamicaConfig()).isEqualTo(config);
        assertThat(dinamica.getCriadoPor()).isEqualTo(utilizador);
    }

    @Test
    void deveGuardarODescritivoSemEspacosNasPontasNaTentativaUmELigadaAoInstrumento() {
        Dinamica dinamica = service.iniciar(instrumentoId, new CriarDinamicaRequest(5L, "  Alteração da planta  "));

        assertThat(dinamica.getDescritivo()).isEqualTo("Alteração da planta");
        assertThat(dinamica.getTentativa()).isEqualTo(1);
        assertThat(dinamica.getInstrumentos()).containsExactly(instrumento);
    }

    @Test
    void deveRegistarAuditoriaDeCriacaoDeDinamica() {
        Dinamica dinamica = service.iniciar(instrumentoId, request);

        verify(auditoriaService).registar("DINAMICA", dinamica.getId().toString(), "CRIACAO", utilizadorId, entidade.getId(), null, null);
    }

    @Test
    void deveLancar404QuandoConfigDeDinamicaNaoExiste() {
        when(instrumentoDinamicaConfigRepository.findById(999L)).thenReturn(Optional.empty());
        CriarDinamicaRequest pedidoInvalido = new CriarDinamicaRequest(999L, "Alteração do PDM");

        assertThatThrownBy(() -> service.iniciar(instrumentoId, pedidoInvalido))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePropagarA404DoInstrumentoServiceQuandoInstrumentoNaoEVisivel() {
        UUID outroId = UUID.randomUUID();
        when(instrumentoService.obter(outroId)).thenThrow(new RecursoNaoEncontradoException("Recurso não encontrado."));

        assertThatThrownBy(() -> service.iniciar(outroId, request)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePropagarO409DoAutorizacaoServiceQuandoNaoElegivel() {
        doThrow(new EstadoInvalidoException("Dinâmica indisponível.")).when(autorizacaoService)
                .verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config);

        assertThatThrownBy(() -> service.iniciar(instrumentoId, request)).isInstanceOf(EstadoInvalidoException.class);

        verify(dinamicaRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(eq("DINAMICA"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void devePermitirDuasIniciacoesDoMesmoTipoQuandoOMotorDeRegrasAsPermiteAmbas() {
        Dinamica primeira = service.iniciar(instrumentoId, request);
        Dinamica segunda = service.iniciar(instrumentoId, request);

        assertThat(primeira).isNotNull();
        assertThat(segunda).isNotNull();
        verify(dinamicaRepository, times(2)).saveAndFlush(any());
    }

    private Dinamica dinamicaEmCurso() {
        Dinamica dinamica = new Dinamica(instrumento, config, "Alteração do PDM", utilizador);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        when(dinamicaRepository.saveAndFlush(dinamica)).thenReturn(dinamica);
        return dinamica;
    }

    private InstrumentoProcedimentoConfig procedimentoObrigatorio(int ordem) {
        InstrumentoProcedimentoConfig procedimento = new InstrumentoProcedimentoConfig(config,
                new TipoProcedimento("PROC-" + ordem, "Procedimento " + ordem), ordem, "SEGUNDA_H");
        ReflectionTestUtils.setField(procedimento, "id", (long) (100 + ordem));
        return procedimento;
    }


    private InstrumentoProcedimentoConfig procedimentoFinal(int ordem) {
        InstrumentoProcedimentoConfig procedimento = procedimentoObrigatorio(ordem);
        when(instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc(config))
                .thenReturn(Optional.of(procedimento));
        return procedimento;
    }

    private void publicado(Dinamica dinamica, InstrumentoProcedimentoConfig procedimento, boolean publicado) {
        when(processoRepository.existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(
                dinamica, procedimento, EstadoProcesso.PUBLICADO)).thenReturn(publicado);
    }

    @Test
    void deveTerminarADinamicaQuandoOProcedimentoFinalEstaPublicadoMesmoSemOsAnterioresPublicados() {
        Dinamica dinamica = dinamicaEmCurso();
        InstrumentoProcedimentoConfig procedimentoFinal = procedimentoFinal(3);
        publicado(dinamica, procedimentoFinal, true);

        service.verificarConclusaoAutomatica(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(dinamica.getDataConclusao()).isNotNull();
        verify(processoRepository).existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(
                dinamica, procedimentoFinal, EstadoProcesso.PUBLICADO);
        verify(auditoriaService).registarTransicao("DINAMICA", dinamica.getId().toString(), "CONCLUSAO_AUTOMATICA",
                new TransicaoEstado("EM_CURSO", "TERMINADA"), utilizadorId, entidade.getId(), null);
        verify(instrumentoService).atualizarEmVigorPorDinamica(dinamica, utilizadorId);
    }

    @Test
    void deveTerminarADinamicaComoAcaoDoSistemaQuandoAPublicacaoChegaSemUtilizador() {
        Dinamica dinamica = dinamicaEmCurso();
        publicado(dinamica, procedimentoFinal(2), true);

        service.verificarConclusaoPeloSistema(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        verify(auditoriaService).registarSistema("DINAMICA", dinamica.getId().toString(), "CONCLUSAO_AUTOMATICA",
                new TransicaoEstado("EM_CURSO", "TERMINADA"), null);
        verify(instrumentoService).atualizarEmVigorPorDinamica(dinamica, null);
        verify(contextoUtilizadorResolver, never()).resolver();
    }

    @Test
    void naoDeveTerminarQuandoOProcedimentoFinalAindaNaoEstaPublicado() {
        Dinamica dinamica = dinamicaEmCurso();
        publicado(dinamica, procedimentoFinal(3), false);

        service.verificarConclusaoAutomatica(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(dinamica.getDataConclusao()).isNull();
        verify(dinamicaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService, instrumentoService);
    }

    @Test
    void aPublicacaoDeUmAtoPontualPelaIncmNaoTerminaADinamica() {
        Dinamica dinamica = dinamicaEmCurso();
        InstrumentoProcedimentoConfig pontual = procedimentoObrigatorio(101);
        pontual.setAtoPontual(true);
        publicado(dinamica, pontual, true);
        publicado(dinamica, procedimentoFinal(3), false);

        service.verificarConclusaoPeloSistema(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        verify(processoRepository, never()).existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(
                dinamica, pontual, EstadoProcesso.PUBLICADO);
        verifyNoInteractions(auditoriaService, instrumentoService);
    }

    @Test
    void deveTerminarUmaDinamicaCaducadaERegistarCaducadaParaTerminada() {
        Dinamica dinamica = dinamicaEmCurso();
        dinamica.setEstado(EstadoDinamica.CADUCADA);
        publicado(dinamica, procedimentoFinal(2), true);

        service.verificarConclusaoAutomatica(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(dinamica.getDataConclusao()).isNotNull();
        verify(auditoriaService).registarTransicao("DINAMICA", dinamica.getId().toString(), "CONCLUSAO_AUTOMATICA",
                new TransicaoEstado("CADUCADA", "TERMINADA"), utilizadorId, entidade.getId(), null);
        verify(instrumentoService).atualizarEmVigorPorDinamica(dinamica, utilizadorId);
    }

    @Test
    void deveRegistarCaducadaParaTerminadaComoAcaoDoSistemaNaConsultaIncm() {
        Dinamica dinamica = dinamicaEmCurso();
        dinamica.setEstado(EstadoDinamica.CADUCADA);
        publicado(dinamica, procedimentoFinal(2), true);

        service.verificarConclusaoPeloSistema(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        verify(auditoriaService).registarSistema("DINAMICA", dinamica.getId().toString(), "CONCLUSAO_AUTOMATICA",
                new TransicaoEstado("CADUCADA", "TERMINADA"), null);
    }

    @Test
    void naoDeveFazerNadaQuandoADinamicaJaEstaTerminada() {
        Dinamica dinamica = dinamicaEmCurso();
        dinamica.setEstado(EstadoDinamica.TERMINADA);

        service.verificarConclusaoAutomatica(dinamica);
        service.verificarConclusaoPeloSistema(dinamica);

        verifyNoInteractions(instrumentoProcedimentoConfigRepository, processoRepository, auditoriaService, instrumentoService);
        verify(dinamicaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveTerminarQuandoNaoHaProcedimentoObrigatorioAtivo() {
        Dinamica dinamica = dinamicaEmCurso();
        when(instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc(config))
                .thenReturn(Optional.empty());

        service.verificarConclusaoAutomatica(dinamica);

        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        verifyNoInteractions(processoRepository, auditoriaService, instrumentoService);
    }
}
