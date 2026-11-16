package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.service.snit.SincronizacaoReferenciaSnitJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SincronizacaoReferenciaSnitAoArrancarTest {

    private static final Duration LOCK_MAXIMO = Duration.ofMinutes(20);

    private final SnitReferenciaRepository snitReferenciaRepository = mock(SnitReferenciaRepository.class);
    private final ExecutorJobs executorJobs = mock(ExecutorJobs.class);
    private final SincronizacaoReferenciaSnitJob job = mock(SincronizacaoReferenciaSnitJob.class);
    private final SincronizacaoReferenciaSnitAoArrancar arranque =
            new SincronizacaoReferenciaSnitAoArrancar(snitReferenciaRepository, executorJobs, job, LOCK_MAXIMO);

    @Test
    void comAReplicaSemConcelhosDeveSincronizarPeloExecutorComONomeEOLockDoJobDiario() {
        when(snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(false);

        arranque.sincronizarSeVazia();

        ArgumentCaptor<Runnable> tarefa = ArgumentCaptor.forClass(Runnable.class);
        verify(executorJobs).executar(eq("sincronizacao-referencia-snit"), eq(LOCK_MAXIMO), tarefa.capture());
        verifyNoInteractions(job);
        tarefa.getValue().run();
        verify(job).executar();
    }

    @Test
    void comConcelhosNaReplicaNaoDeveSincronizar() {
        when(snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(true);

        arranque.sincronizarSeVazia();

        verify(executorJobs, never()).executar(anyString(), any(), any());
        verifyNoInteractions(job);
    }
}
