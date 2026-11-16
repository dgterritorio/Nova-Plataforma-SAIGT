package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LogJson.Extensao.class)
class EncoderLogJsonTest {

    private static final Logger LOG = LoggerFactory.getLogger(EncoderLogJsonTest.class);

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    private static String unicaLinha(LogJson log) {
        assertThat(log.linhas()).hasSize(1);
        return log.linhas().getFirst();
    }

    @Test
    void deveEscreverCadaEventoNumaSoLinhaJsonComInstanteNivelLoggerThreadEMensagem(LogJson log) {
        Instant antes = Instant.now();

        LOG.info("primeira linha\nsegunda linha\r\nterceira \"entre aspas\"");

        String linha = unicaLinha(log);
        assertThat(linha).endsWith("\n");
        assertThat(linha.substring(0, linha.length() - 1)).doesNotContain("\n").doesNotContain("\r");
        JsonNode evento = LogJson.ler(linha);
        assertThat(evento.path("@timestamp").asText()).endsWith("Z");
        assertThat(Instant.parse(evento.path("@timestamp").asText()))
                .isBetween(antes.minus(Duration.ofMillis(1)), Instant.now());
        assertThat(evento.path("level").asText()).isEqualTo("INFO");
        assertThat(evento.path("logger").asText()).isEqualTo(EncoderLogJsonTest.class.getName());
        assertThat(evento.path("thread").asText()).isEqualTo(Thread.currentThread().getName());
        assertThat(evento.path("message").asText()).isEqualTo("primeira linha\nsegunda linha\r\nterceira \"entre aspas\"");
    }

    @Test
    void deveEscreverAExcecaoNumaSoLinhaComAClasseAMensagemEOStackTraceEmCampos(LogJson log) {
        IllegalStateException erro = new IllegalStateException("falhou\nem duas linhas",
                new java.io.IOException("causa da falha"));

        LOG.error("Pedido falhou", erro);

        String linha = unicaLinha(log);
        assertThat(linha.substring(0, linha.length() - 1)).doesNotContain("\n");
        JsonNode evento = LogJson.ler(linha);
        assertThat(evento.path("level").asText()).isEqualTo("ERROR");
        assertThat(evento.path("message").asText()).isEqualTo("Pedido falhou");
        assertThat(evento.path("exceptionClass").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(evento.path("exceptionMessage").asText()).isEqualTo("falhou\nem duas linhas");
        assertThat(evento.path("stackTrace").asText())
                .contains(IllegalStateException.class.getName())
                .contains("at " + EncoderLogJsonTest.class.getName())
                .contains("Caused by: java.io.IOException: causa da falha");
    }

    @Test
    void deveEscreverOCorrelationIdDoMdcSemAChaveInternaESemOutrasChavesDoMdc(LogJson log) {
        MDC.put("apiTraceId", "abc-123");
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        MDC.put("outraChave", "valor-que-nao-sai-6d2");

        LOG.info("evento de um pedido");

        JsonNode evento = LogJson.ler(unicaLinha(log));
        assertThat(evento.path("correlationId").asText()).isEqualTo("abc-123");
        assertThat(evento.path("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(evento.path("spanId").asText()).isEqualTo("00f067aa0ba902b7");
        assertThat(evento.has("apiTraceId")).isFalse();
        assertThat(log.todas()).doesNotContain("valor-que-nao-sai-6d2");
    }

    @Test
    void naoDeveTerCorrelationIdNumEventoSemPedido(LogJson log) {
        MDC.put(EncoderLogJson.MDC_EXECUCAO_JOB, "execucao-1");

        LOG.info("Job iniciado");

        JsonNode evento = LogJson.ler(unicaLinha(log));
        assertThat(evento.has("correlationId")).isFalse();
        assertThat(evento.path("execucaoJob").asText()).isEqualTo("execucao-1");
    }

    @Test
    void deveEscreverOsParesChaveValorDoEventoComoCampos(LogJson log) {
        LOG.atInfo().addKeyValue("sistema", "SNIT").addKeyValue("estadoHttp", 201).addKeyValue("duracaoMs", 37L)
                .log("Chamada externa");

        JsonNode evento = LogJson.ler(unicaLinha(log));
        assertThat(evento.path("sistema").asText()).isEqualTo("SNIT");
        assertThat(evento.path("estadoHttp").isInt()).isTrue();
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(201);
        assertThat(evento.path("duracaoMs").asLong()).isEqualTo(37L);
    }
}
