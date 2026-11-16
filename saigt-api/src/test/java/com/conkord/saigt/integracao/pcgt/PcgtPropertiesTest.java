package com.conkord.saigt.integracao.pcgt;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PcgtPropertiesTest {

    @EnableConfigurationProperties(PcgtProperties.class)
    static class Configuracao {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
            .withUserConfiguration(Configuracao.class);

    @Test
    void deveMascararOUtilizadorEASenhaNoToString() {
        PcgtProperties propriedades = new PcgtProperties("http://pcgt.exemplo", "conta-teste", "senha-teste-x9",
                Duration.ofSeconds(5), Duration.ofSeconds(10));

        assertThat(propriedades.toString()).contains("username=***").contains("password=***")
                .contains("http://pcgt.exemplo").doesNotContain("conta-teste").doesNotContain("senha-teste-x9");
    }

    @Test
    void deveLerAsCredenciaisDasVariaveisPcgtESemUrlFicaNaoConfigurado() {
        contextRunner.withPropertyValues("PCGT_USERNAME=conta-teste", "PCGT_PASSWORD=senha-teste-x9").run(context -> {
            PcgtProperties propriedades = context.getBean(PcgtProperties.class);
            assertThat(propriedades.baseUrl()).isEmpty();
            assertThat(propriedades.username()).isEqualTo("conta-teste");
            assertThat(propriedades.password()).isEqualTo("senha-teste-x9");
            assertThat(propriedades.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(propriedades.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void deveLimitarA300PedidosPorMinutoComEsperaDeUmMinutoConfiguraveis() {
        contextRunner.run(context -> {
            RateLimiterConfig config = context.getBean(RateLimiterRegistry.class).rateLimiter(ClientePcgt.LIMITE_PEDIDOS)
                    .getRateLimiterConfig();
            assertThat(config.getLimitForPeriod()).isEqualTo(300);
            assertThat(config.getLimitRefreshPeriod()).isEqualTo(Duration.ofMinutes(1));
            assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofMinutes(1));
        });
        contextRunner.withPropertyValues("APP_PCGT_RATE_LIMIT=150", "APP_PCGT_RATE_LIMIT_ESPERA=30s").run(context -> {
            RateLimiterConfig config = context.getBean(RateLimiterRegistry.class).rateLimiter(ClientePcgt.LIMITE_PEDIDOS)
                    .getRateLimiterConfig();
            assertThat(config.getLimitForPeriod()).isEqualTo(150);
            assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(30));
        });
    }
}
