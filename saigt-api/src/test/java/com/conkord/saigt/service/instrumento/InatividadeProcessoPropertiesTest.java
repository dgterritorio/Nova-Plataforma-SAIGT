package com.conkord.saigt.service.instrumento;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InatividadeProcessoPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(InatividadeProcessoProperties.class)
    static class ComPropriedades {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ComPropriedades.class);

    @Test
    void deveUsarPorOmissaoTresMesesTresMesesEQuinzeDiasEQuatroMeses() {
        contextRunner.run(context -> {
            InatividadeProcessoProperties prazos = context.getBean(InatividadeProcessoProperties.class);
            assertThat(prazos.primeiroAviso()).isEqualTo(Period.ofMonths(3));
            assertThat(prazos.segundoAviso()).isEqualTo(Period.of(0, 3, 15));
            assertThat(prazos.anulacao()).isEqualTo(Period.ofMonths(4));
            assertThat(context.getEnvironment().getProperty("app.processos.inatividade.cron")).isEqualTo("0 30 5 * * *");
            assertThat(context.getEnvironment().getProperty("app.processos.inatividade.lock-maximo")).isEqualTo("30m");
        });
    }

    @Test
    void deveAceitarPrazosDasVariaveisDeAmbiente() {
        contextRunner.withPropertyValues("PROCESSOS_INATIVIDADE_PRIMEIRO_AVISO=P10D",
                        "PROCESSOS_INATIVIDADE_SEGUNDO_AVISO=P20D", "PROCESSOS_INATIVIDADE_ANULACAO=P1M")
                .run(context -> assertThat(context.getBean(InatividadeProcessoProperties.class))
                        .isEqualTo(new InatividadeProcessoProperties(Period.ofDays(10), Period.ofDays(20),
                                Period.ofMonths(1))));
    }

    @Test
    void deveFalharOArranqueQuandoOSegundoAvisoNaoEPosteriorAoPrimeiro() {
        contextRunner.withPropertyValues("PROCESSOS_INATIVIDADE_SEGUNDO_AVISO=P2M")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("têm de ser crescentes");
                });
    }

    @Test
    void deveRecusarPrazosSemOrdemFixaComoUmMesETrintaDias() {
        assertThatThrownBy(() -> new InatividadeProcessoProperties(Period.ofDays(30), Period.ofMonths(1),
                Period.ofMonths(2))).hasMessageContaining("têm de ser crescentes");
    }

    @Test
    void deveRecusarUmPrimeiroAvisoNuloOuNegativo() {
        assertThatThrownBy(() -> new InatividadeProcessoProperties(Period.ZERO, Period.ofMonths(1),
                Period.ofMonths(2))).hasMessageContaining("têm de ser crescentes");
        assertThatThrownBy(() -> new InatividadeProcessoProperties(null, Period.ofMonths(1), Period.ofMonths(2)))
                .isInstanceOf(NullPointerException.class);
    }
}
