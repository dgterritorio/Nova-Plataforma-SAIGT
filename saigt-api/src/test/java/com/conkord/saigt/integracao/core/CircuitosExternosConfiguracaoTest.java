package com.conkord.saigt.integracao.core;

import com.conkord.saigt.configuration.LogJson;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LogJson.Extensao.class)
class CircuitosExternosConfiguracaoTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
            .withUserConfiguration(ExecutorChamadasExternas.class);

    @Test
    void deveTerOsCircuitosDeTodosOsSistemasExternosNoArranque() {
        contextRunner.run(context -> assertThat(context.getBean(CircuitBreakerRegistry.class).getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName)
                .contains("incm-autenticacao", "incm-submissao", "snit-consulta", "snit-registo", "pcgt", "geoconform"));
    }

    @Test
    void deveIgnorarOs4xxSoNoCircuitoDoRegistoNoSnitComOsValoresDoSnit() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            CircuitBreakerConfig registo = registry.circuitBreaker("snit-registo").getCircuitBreakerConfig();
            CircuitBreakerConfig consulta = registry.circuitBreaker("snit-consulta").getCircuitBreakerConfig();
            assertThat(List.of(409, 400, 401)).allSatisfy(codigo -> assertThat(registo.getIgnoreExceptionPredicate()
                    .test(HttpClientErrorException.create(HttpStatus.valueOf(codigo), "", null, null, null))).isTrue());
            assertThat(registo.getIgnoreExceptionPredicate().test(
                    HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "", null, null, null))).isFalse();
            assertThat(consulta.getIgnoreExceptionPredicate().test(
                    HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null))).isFalse();
            assertThat(registo.getSlidingWindowSize()).isEqualTo(10);
            assertThat(registo.getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(registo.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(registo.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
            assertThat(registo.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        });
    }

    @Test
    void deveConfigurarOsCircuitosDaPcgtEDoGeoconformComOsValoresPorOmissaoDaIncm() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            assertThat(List.of("pcgt", "geoconform")).allSatisfy(nome -> {
                CircuitBreakerConfig config = registry.circuitBreaker(nome).getCircuitBreakerConfig();
                assertThat(config.getSlidingWindowSize()).isEqualTo(10);
                assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
                assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
                assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
                assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            });
        });
    }

    @Test
    void deveUsarAsVariaveisAppPcgtCbEAppGeoconformCb() {
        contextRunner.withPropertyValues("APP_PCGT_CB_MIN_CALLS=7", "APP_GEOCONFORM_CB_WAIT_OPEN=45s")
                .run(context -> {
                    CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                    assertThat(registry.circuitBreaker("pcgt").getCircuitBreakerConfig().getMinimumNumberOfCalls())
                            .isEqualTo(7);
                    assertThat(registry.circuitBreaker("geoconform").getCircuitBreakerConfig()
                            .getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(45_000L);
                });
    }

    @Test
    void deveIgnorarOs4xxNoCircuitoDaPcgt() {
        contextRunner.run(context -> {
            CircuitBreakerConfig pcgt = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("pcgt")
                    .getCircuitBreakerConfig();
            assertThat(List.of(400, 401, 403, 404, 429)).allSatisfy(codigo -> assertThat(pcgt.getIgnoreExceptionPredicate()
                    .test(HttpClientErrorException.create(HttpStatus.valueOf(codigo), "", null, null, null))).isTrue());
            assertThat(pcgt.getIgnoreExceptionPredicate().test(
                    HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null))).isFalse();
        });
    }

    @Test
    void deveIgnorarOs4xxNoCircuitoDoGeoconformEContarOs5xx() {
        contextRunner.run(context -> {
            CircuitBreakerConfig geoconform = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("geoconform")
                    .getCircuitBreakerConfig();
            assertThat(List.of(401, 403, 409, 422)).allSatisfy(codigo -> assertThat(geoconform
                    .getIgnoreExceptionPredicate()
                    .test(HttpClientErrorException.create(HttpStatus.valueOf(codigo), "", null, null, null))).isTrue());
            assertThat(geoconform.getIgnoreExceptionPredicate().test(
                    HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null))).isFalse();
        });
    }

    @Test
    void deveEscreverUmEventoWarnNaTransicaoDeUmCircuitoDasProperties(LogJson log) {
        contextRunner.run(context -> context.getBean(CircuitBreakerRegistry.class).circuitBreaker("geoconform")
                .transitionToOpenState());

        assertThat(ExecutorChamadasExternasTest.transicoes(log, "geoconform")).singleElement().satisfies(evento -> {
            assertThat(evento.path("level").asText()).isEqualTo("WARN");
            assertThat(evento.path("estadoOrigem").asText()).isEqualTo("CLOSED");
            assertThat(evento.path("estadoDestino").asText()).isEqualTo("OPEN");
        });
    }
}
