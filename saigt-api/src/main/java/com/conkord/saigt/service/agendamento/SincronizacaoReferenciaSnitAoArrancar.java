package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.service.snit.SincronizacaoReferenciaSnitJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Numa base sem concelhos na réplica do SNIT (ambiente novo), sincroniza-a ao arrancar em vez de esperar pelo job das
 * 3h: o formulário do IGT novo lê a NUT II e os concelhos só da réplica. Mesmo nome e lock do job diário.
 */
@Component
@ConditionalOnProperty(name = "app.snit.referencia.sincronizar-ao-arrancar", havingValue = "true", matchIfMissing = true)
public class SincronizacaoReferenciaSnitAoArrancar {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoReferenciaSnitAoArrancar.class);

    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ExecutorJobs executorJobs;
    private final SincronizacaoReferenciaSnitJob sincronizacaoReferenciaSnitJob;
    private final Duration lockMaximo;

    public SincronizacaoReferenciaSnitAoArrancar(SnitReferenciaRepository snitReferenciaRepository, ExecutorJobs executorJobs,
                                                 SincronizacaoReferenciaSnitJob sincronizacaoReferenciaSnitJob,
                                                 @Value("${app.snit.referencia.lock-maximo}") Duration lockMaximo) {
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.executorJobs = executorJobs;
        this.sincronizacaoReferenciaSnitJob = sincronizacaoReferenciaSnitJob;
        this.lockMaximo = lockMaximo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sincronizarSeVazia() {
        if (snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)) {
            return;
        }
        log.info("Réplica do SNIT sem concelhos: sincronização ao arrancar | job={}", JobsAgendados.SINCRONIZACAO_REFERENCIA_SNIT);
        executorJobs.executar(JobsAgendados.SINCRONIZACAO_REFERENCIA_SNIT, lockMaximo, sincronizacaoReferenciaSnitJob::executar);
    }
}
