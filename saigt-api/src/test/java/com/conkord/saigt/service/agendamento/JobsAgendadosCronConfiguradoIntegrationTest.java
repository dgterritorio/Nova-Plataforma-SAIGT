package com.conkord.saigt.service.agendamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As variáveis de ambiente `INCM_CONSULTA_CRON`, `SNIT_REFERENCIA_CRON` e `PROCESSOS_INATIVIDADE_CRON` substituem o
 * cron por omissão do `consulta-incm`, do `sincronizacao-referencia-snit` e do `inatividade-processos`.
 */
@SpringBootTest(properties = {"INCM_CONSULTA_CRON=0 * * * * *", "SNIT_REFERENCIA_CRON=0 0 4 * * *",
        "PROCESSOS_INATIVIDADE_CRON=0 * * * * *"})
@ActiveProfiles("test")
class JobsAgendadosCronConfiguradoIntegrationTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor agendador;

    @Test
    void deveUsarOCronDeIncmConsultaCronQuandoDefinido() {
        assertThat(cronDoMetodo("consultarIncm")).isEqualTo("0 * * * * *");
    }

    @Test
    void deveAgendarASincronizacaoDaReferenciaDoSnitComOCronDeSnitReferenciaCron() {
        assertThat(cronDoMetodo("sincronizarReferenciaSnit")).isEqualTo("0 0 4 * * *");
    }

    @Test
    void deveCorrerAVerificacaoDeInatividadeACadaMinutoComProcessosInatividadeCron() {
        assertThat(cronDoMetodo("verificarInatividade")).isEqualTo("0 * * * * *");
    }

    private String cronDoMetodo(String metodo) {
        return agendador.getScheduledTasks().stream()
                .map(tarefa -> (CronTask) tarefa.getTask())
                .filter(tarefa -> ((ScheduledMethodRunnable) tarefa.getRunnable()).getMethod().getName()
                        .equals(metodo))
                .map(CronTask::getExpression)
                .findFirst().orElseThrow();
    }
}
