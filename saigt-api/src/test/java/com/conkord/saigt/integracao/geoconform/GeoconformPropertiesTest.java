package com.conkord.saigt.integracao.geoconform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GeoconformPropertiesTest {

    private static final String CLIENTE = "cliente-teste-gc";
    private static final String SEGREDO = "segredo-teste-gc-4d2b";

    @EnableConfigurationProperties(GeoconformProperties.class)
    static class Configuracao {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Configuracao.class);

    private static GeoconformProperties propriedades(String baseUrl, String tokenUrl, String cliente, String segredo) {
        return new GeoconformProperties(baseUrl, tokenUrl, cliente, segredo, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    void deveMascararOClienteEOSegredoNoToString() {
        GeoconformProperties propriedades = propriedades("http://gc.exemplo", "http://gc.exemplo/token", CLIENTE, SEGREDO);

        assertThat(propriedades.toString()).contains("clientId=***").contains("clientSecret=***")
                .contains("http://gc.exemplo").contains("http://gc.exemplo/token")
                .doesNotContain(CLIENTE).doesNotContain(SEGREDO);
    }

    @Test
    void deveLerAsVariaveisGeoconformComOsTemposPorOmissaoESemUrlFicaNaoConfigurado() {
        contextRunner.withPropertyValues("GEOCONFORM_CLIENT_ID=" + CLIENTE, "GEOCONFORM_CLIENT_SECRET=" + SEGREDO)
                .run(context -> {
                    GeoconformProperties propriedades = context.getBean(GeoconformProperties.class);
                    assertThat(propriedades.baseUrl()).isEmpty();
                    assertThat(propriedades.tokenUrl()).isEmpty();
                    assertThat(propriedades.clientId()).isEqualTo(CLIENTE);
                    assertThat(propriedades.clientSecret()).isEqualTo(SEGREDO);
                    assertThat(propriedades.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(propriedades.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(propriedades.configurado()).isFalse();
                });
        contextRunner.withPropertyValues("GEOCONFORM_BASE_URL=http://gc.exemplo",
                "GEOCONFORM_TOKEN_URL=http://gc.exemplo/token", "GEOCONFORM_CLIENT_ID=" + CLIENTE,
                "GEOCONFORM_CLIENT_SECRET=" + SEGREDO, "GEOCONFORM_READ_TIMEOUT=3s").run(context -> {
                    GeoconformProperties propriedades = context.getBean(GeoconformProperties.class);
                    assertThat(propriedades.configurado()).isTrue();
                    assertThat(propriedades.readTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    void naoFicaConfiguradoSemUrlBaseSemUrlDoTokenOuSemCredenciais() {
        assertThat(propriedades("", "http://gc/token", CLIENTE, SEGREDO).configurado()).isFalse();
        assertThat(propriedades("http://gc", " ", CLIENTE, SEGREDO).configurado()).isFalse();
        assertThat(propriedades("http://gc", "http://gc/token", null, SEGREDO).configurado()).isFalse();
        assertThat(propriedades("http://gc", "http://gc/token", CLIENTE, "").configurado()).isFalse();
        assertThat(propriedades("http://gc", "http://gc/token", CLIENTE, SEGREDO).configurado()).isTrue();
    }
}
