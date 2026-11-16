package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.configuration.EncoderLogJson;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Corre um job periódico numa só instância de cada vez, com o lock na tabela `shedlock`. O lock mínimo impede que
 * outra instância, cujo cron dispara uns instantes depois, volte a correr um job rápido que acabou de terminar.
 */
@Service
public class ExecutorJobs {

    private static final Logger log = LoggerFactory.getLogger(ExecutorJobs.class);

    private final LockProvider lockProvider;
    private final Duration lockMinimo;

    public ExecutorJobs(LockProvider lockProvider, @Value("${app.jobs.lock-minimo}") Duration lockMinimo) {
        this.lockProvider = lockProvider;
        this.lockMinimo = lockMinimo;
    }

    /**
     * Nunca propaga a exceção da tarefa: fica no log e a execução seguinte corre normalmente. Os eventos de uma execução
     * partilham o `execucaoJob`; não levam `correlationId`, que é só dos pedidos.
     */
    public void executar(String nome, Duration lockMaximo, Runnable tarefa) {
        MDC.put(EncoderLogJson.MDC_EXECUCAO_JOB, UUID.randomUUID().toString());
        try {
            Optional<SimpleLock> lock = lockProvider.lock(
                    new LockConfiguration(Instant.now(), nome, lockMaximo, lockMinimo));
            if (lock.isEmpty()) {
                log.debug("Job não executado: lock ocupado por outra instância | job={}", nome);
                return;
            }
            try {
                correr(nome, tarefa);
            } finally {
                lock.get().unlock();
            }
        } catch (RuntimeException e) {
            log.error("Job não executado: falha no lock | job={}", nome, e);
        } finally {
            MDC.remove(EncoderLogJson.MDC_EXECUCAO_JOB);
        }
    }

    private static void correr(String nome, Runnable tarefa) {
        log.info("Job iniciado | job={}", nome);
        long inicio = System.nanoTime();
        try {
            tarefa.run();
            log.info("Job terminado | job={} duracaoMs={} resultado=OK", nome, duracaoMs(inicio));
        } catch (RuntimeException e) {
            log.error("Job terminado | job={} duracaoMs={} resultado=ERRO", nome, duracaoMs(inicio), e);
        }
    }

    private static long duracaoMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }
}
