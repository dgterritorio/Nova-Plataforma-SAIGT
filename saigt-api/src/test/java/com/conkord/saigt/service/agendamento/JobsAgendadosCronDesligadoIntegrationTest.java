package com.conkord.saigt.service.agendamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "SNIT_REFERENCIA_CRON=-")
@ActiveProfiles("test")
class JobsAgendadosCronDesligadoIntegrationTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor agendador;

    @Test
    void naoDeveAgendarASincronizacaoDaReferenciaDoSnitComOCronDesligado() {
        assertThat(agendador.getScheduledTasks().stream()
                .map(tarefa -> ((ScheduledMethodRunnable) ((CronTask) tarefa.getTask()).getRunnable())
                        .getMethod().getName()))
                .doesNotContain("sincronizarReferenciaSnit")
                .contains("consultarIncm", "purgarSessoes");
    }
}
