package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.service.documento.PurgaIpDescargaJob;
import com.conkord.saigt.service.documento.LimpezaArmazenamentoService;
import com.conkord.saigt.service.documento.RetencaoRegistoExternoService;
import com.conkord.saigt.service.incm.ConsultaIncmJob;
import com.conkord.saigt.service.instrumento.CaducidadeDinamicaJob;
import com.conkord.saigt.service.instrumento.InatividadeProcessoJob;
import com.conkord.saigt.service.sessao.SessaoService;
import com.conkord.saigt.service.snit.SincronizacaoReferenciaSnitJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JobsAgendadosTest {

    private static final Duration LOCK_MAXIMO_PURGA = Duration.ofMinutes(15);
    private static final Duration LOCK_MAXIMO_LIMPEZA = Duration.ofHours(2);
    private static final Duration LOCK_MAXIMO_PURGA_IP = Duration.ofHours(1);
    private static final Duration LOCK_MAXIMO_CONSULTA_INCM = Duration.ofMinutes(30);
    private static final Duration LOCK_MAXIMO_REFERENCIA_SNIT = Duration.ofMinutes(20);
    private static final Duration LOCK_MAXIMO_CADUCIDADE_DINAMICAS = Duration.ofMinutes(25);
    private static final Duration LOCK_MAXIMO_INATIVIDADE_PROCESSOS = Duration.ofMinutes(35);
    private static final Duration LOCK_MAXIMO_RETENCAO_REGISTO_EXTERNO = Duration.ofMinutes(40);

    private final ExecutorJobs executorJobs = mock(ExecutorJobs.class);
    private final SessaoService sessaoService = mock(SessaoService.class);
    private final LimpezaArmazenamentoService limpezaArmazenamentoService = mock(LimpezaArmazenamentoService.class);
    private final PurgaIpDescargaJob purgaIpDescargaJob = mock(PurgaIpDescargaJob.class);
    private final ConsultaIncmJob consultaIncmJob = mock(ConsultaIncmJob.class);
    private final SincronizacaoReferenciaSnitJob sincronizacaoReferenciaSnitJob = mock(SincronizacaoReferenciaSnitJob.class);
    private final CaducidadeDinamicaJob caducidadeDinamicaJob = mock(CaducidadeDinamicaJob.class);
    private final InatividadeProcessoJob inatividadeProcessoJob = mock(InatividadeProcessoJob.class);
    private final RetencaoRegistoExternoService retencaoRegistoExternoService = mock(RetencaoRegistoExternoService.class);
    private final JobsAgendados jobs = new JobsAgendados(executorJobs, sessaoService, limpezaArmazenamentoService,
            purgaIpDescargaJob, consultaIncmJob, sincronizacaoReferenciaSnitJob, caducidadeDinamicaJob, inatividadeProcessoJob,
            retencaoRegistoExternoService,
            LOCK_MAXIMO_PURGA, LOCK_MAXIMO_LIMPEZA, LOCK_MAXIMO_PURGA_IP, LOCK_MAXIMO_CONSULTA_INCM, LOCK_MAXIMO_REFERENCIA_SNIT, LOCK_MAXIMO_CADUCIDADE_DINAMICAS,
            LOCK_MAXIMO_INATIVIDADE_PROCESSOS, LOCK_MAXIMO_RETENCAO_REGISTO_EXTERNO);

    @Test
    void deveRepetirARetencaoDoRegistoExternoAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.repetirRetencaoRegistoExterno();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("retencao-registo-externo"), eq(LOCK_MAXIMO_RETENCAO_REGISTO_EXTERNO), tarefa.capture());
        verifyNoInteractions(retencaoRegistoExternoService);
        tarefa.getValue().run();
        verify(retencaoRegistoExternoService).repetirPendentes();
    }

    @Test
    void devePurgarSessoesAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.purgarSessoes();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("purga-sessoes"), eq(LOCK_MAXIMO_PURGA), tarefa.capture());
        verifyNoInteractions(sessaoService);
        tarefa.getValue().run();
        verify(sessaoService).purgar();
    }

    @Test
    void deveLimparOArmazenamentoAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.limparArmazenamento();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("limpeza-armazenamento"), eq(LOCK_MAXIMO_LIMPEZA), tarefa.capture());
        verifyNoInteractions(limpezaArmazenamentoService);
        tarefa.getValue().run();
        ArgumentCaptor<Instant> agora = ArgumentCaptor.forClass(Instant.class);
        verify(limpezaArmazenamentoService).limpar(agora.capture());
        assertThat(agora.getValue()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void devePurgarIpDasDescargasAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.purgarIpDescargas();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("purga-ip-descargas"), eq(LOCK_MAXIMO_PURGA_IP), tarefa.capture());
        verifyNoInteractions(purgaIpDescargaJob);
        tarefa.getValue().run();
        verify(purgaIpDescargaJob).executar();
    }

    @Test
    void deveConsultarIncmAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.consultarIncm();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("consulta-incm"), eq(LOCK_MAXIMO_CONSULTA_INCM), tarefa.capture());
        verifyNoInteractions(consultaIncmJob);
        tarefa.getValue().run();
        verify(consultaIncmJob).executar();
    }

    @Test
    void deveSincronizarAReferenciaDoSnitAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.sincronizarReferenciaSnit();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("sincronizacao-referencia-snit"), eq(LOCK_MAXIMO_REFERENCIA_SNIT),
                tarefa.capture());
        verifyNoInteractions(sincronizacaoReferenciaSnitJob);
        tarefa.getValue().run();
        verify(sincronizacaoReferenciaSnitJob).executar();
    }

    @Test
    void deveVerificarACaducidadeDasDinamicasAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.verificarCaducidade();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("caducidade-dinamicas"), eq(LOCK_MAXIMO_CADUCIDADE_DINAMICAS), tarefa.capture());
        verifyNoInteractions(caducidadeDinamicaJob);
        tarefa.getValue().run();
        verify(caducidadeDinamicaJob).executar();
    }

    @Test
    void deveVerificarAInatividadeDosProcessosAtravesDoExecutorComONomeEOLockMaximoDoJob() {
        jobs.verificarInatividade();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("inatividade-processos"), eq(LOCK_MAXIMO_INATIVIDADE_PROCESSOS),
                tarefa.capture());
        verifyNoInteractions(inatividadeProcessoJob);
        tarefa.getValue().run();
        verify(inatividadeProcessoJob).executar();
    }
}
