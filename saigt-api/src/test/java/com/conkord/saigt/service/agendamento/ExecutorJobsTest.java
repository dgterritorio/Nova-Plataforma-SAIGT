package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.configuration.LogJson;
import com.fasterxml.jackson.databind.JsonNode;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(LogJson.Extensao.class)
class ExecutorJobsTest {

    @Test
    void osEventosDeUmJobNaoTemCorrelationIdEPartilhamOIdDaExecucao(LogJson log) {
        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(mock(SimpleLock.class)));
        ExecutorJobs executor = new ExecutorJobs(lockProvider, Duration.ZERO);

        executor.executar("job-de-teste", Duration.ofMinutes(1),
                () -> LoggerFactory.getLogger("teste.job").info("evento escrito pela tarefa"));

        List<JsonNode> eventos = log.eventos();
        assertThat(eventos).hasSize(3).allSatisfy(evento -> assertThat(evento.has("correlationId")).isFalse());
        String execucao = eventos.getFirst().path("execucaoJob").asText();
        assertThat(execucao).isNotBlank();
        assertThat(eventos).allSatisfy(evento -> assertThat(evento.path("execucaoJob").asText()).isEqualTo(execucao));
    }
}
