package com.conkord.saigt.service.agendamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os únicos `@Scheduled` da aplicação são os de {@link JobsAgendados}, com os crons por omissão. Sem
 * `SNIT_REFERENCIA_CRON`, a sincronização da réplica do SNIT corre todos os dias às 03:00.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobsAgendadosIntegrationTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor agendador;

    @Test
    void deveAgendarOsJobsNoMecanismoNovoComOsCronsPorOmissao() {
        Map<String, String> cronPorMetodo = agendador.getScheduledTasks().stream()
                .map(tarefa -> (CronTask) tarefa.getTask())
                .collect(Collectors.toMap(JobsAgendadosIntegrationTest::nomeDoMetodo,CronTask::getExpression));

        assertThat(cronPorMetodo).containsExactlyInAnyOrderEntriesOf(Map.of(
                "JobsAgendados.purgarSessoes", "0 0 3 * * *",
                "JobsAgendados.limparArmazenamento", "0 30 3 * * *",
                "JobsAgendados.purgarIpDescargas", "0 0 4 * * *",
                "JobsAgendados.consultarIncm", "0 0 6 * * *",
                "JobsAgendados.sincronizarReferenciaSnit", "0 0 3 * * *",
                "JobsAgendados.verificarCaducidade", "0 0 5 * * *",
                "JobsAgendados.verificarInatividade", "0 30 5 * * *",
                "JobsAgendados.repetirRetencaoRegistoExterno", "0 15 * * * *"));
    }

    private static String nomeDoMetodo(CronTask tarefa) {
        ScheduledMethodRunnable metodo = (ScheduledMethodRunnable) tarefa.getRunnable();
        return metodo.getMethod().getDeclaringClass().getSimpleName() + "." + metodo.getMethod().getName();
    }
}
