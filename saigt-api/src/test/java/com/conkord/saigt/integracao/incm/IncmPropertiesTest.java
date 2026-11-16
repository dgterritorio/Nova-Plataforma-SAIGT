package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncmPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
            .withUserConfiguration(ExecutorChamadasExternas.class, ClienteIncm.class);

    @Test
    void deveUsar5sNaLigacaoE30sNaLeituraSemVariaveisDeTimeout() {
        contextRunner.run(context -> {
            IncmProperties propriedades = context.getBean(IncmProperties.class);
            assertThat(propriedades.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(propriedades.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void deveUsarOsTimeoutsDeIncmConnectTimeoutEIncmReadTimeout() {
        contextRunner.withPropertyValues("INCM_CONNECT_TIMEOUT=2s", "INCM_READ_TIMEOUT=45s")
                .run(context -> {
                    IncmProperties propriedades = context.getBean(IncmProperties.class);
                    assertThat(propriedades.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(propriedades.readTimeout()).isEqualTo(Duration.ofSeconds(45));
                });
    }

    @Test
    void deveAplicarAoClienteOReadTimeoutDefinidoNoArranque() {
        try (ServidorIncmTeste servidor = ServidorIncmTeste.iniciar()) {
            servidor.reterResposta("/GetActDetails");
            contextRunner.withPropertyValues("INCM_BASE_URL=" + servidor.url(), "INCM_SERVICE_KEY=chave",
                            "INCM_READ_TIMEOUT=300ms")
                    .run(context -> {
                        ResultadoChamadaIncm<GetActDetailsResponse> resultado =
                                context.getBean(ClienteIncm.class).obterDetalhesAto(UUID.randomUUID(), 1L);

                        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.TIMEOUT);
                        assertThat(resultado.duracaoMs()).isLessThan(5000L);
                    });
        }
    }

    @Test
    void deveFalharArranqueComReadTimeoutVazio() {
        contextRunner.withPropertyValues("INCM_READ_TIMEOUT=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("readTimeout");
                });
    }

    @Test
    void naoDeveMostrarOServiceKeyNoToString() {
        IncmProperties propriedades = new IncmProperties("http://incm", "segredo-incm", Duration.ofSeconds(5),
                Duration.ofSeconds(30));

        assertThat(propriedades.toString()).doesNotContain("segredo-incm").contains("serviceKey=***");
    }
}
