package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDescargasTest {

    private static LimiteDescargas limite(int global, int porClienteSnit) {
        return new LimiteDescargas(new DescargaProperties(List.of("k:chave"), "k", Duration.ofSeconds(60), global, 3,
                Period.ofMonths(12), Duration.ofSeconds(5), porClienteSnit));
    }

    @Test
    void deveLimitarAsDescargasEmSimultaneoPorCliente() {
        LimiteDescargas limite = limite(50, 2);

        assertThat(limite.ocuparCliente("a")).isTrue();
        assertThat(limite.ocuparCliente("a")).isTrue();
        assertThat(limite.ocuparCliente("a")).isFalse();
        assertThat(limite.ocuparCliente("b")).isTrue();

        limite.libertarCliente("a");
        assertThat(limite.ocuparCliente("a")).isTrue();
    }

    @Test
    void deveRespeitarOLimiteGlobalEDevolverOLugarQuandoOClienteEstaNoLimite() {
        LimiteDescargas limite = limite(2, 5);

        assertThat(limite.ocuparCliente("a")).isTrue();
        assertThat(limite.ocuparCliente("b")).isTrue();
        assertThat(limite.ocuparCliente("c")).isFalse();

        limite.libertarCliente("a");
        assertThat(limite.ocupar()).isTrue();
        assertThat(limite.ocupar()).isFalse();
    }
}
