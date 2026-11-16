package com.conkord.saigt.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captura os eventos de log já codificados pelo {@link EncoderLogJson} (o mesmo do `logback-spring.xml`), no momento
 * em que são escritos, para os testes lerem os campos JSON. Uso: `@ExtendWith(LogJson.Extensao.class)` e um parâmetro
 * `LogJson` no teste, ou {@link #capturar()} e {@link #parar()}.
 */
public final class LogJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> linhas = new CopyOnWriteArrayList<>();
    private final Logger raiz;
    private final EncoderLogJson encoder;
    private final AppenderBase<ILoggingEvent> appender;

    private LogJson() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        raiz = contexto.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        encoder = new EncoderLogJson();
        encoder.setContext(contexto);
        encoder.start();
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent evento) {
                linhas.add(new String(encoder.encode(evento), StandardCharsets.UTF_8));
            }
        };
        appender.setContext(contexto);
        appender.start();
        raiz.addAppender(appender);
    }

    public static LogJson capturar() {
        return new LogJson();
    }

    public void parar() {
        raiz.detachAppender(appender);
        appender.stop();
        encoder.stop();
    }

    /** As linhas tal como saem na saída padrão, com a quebra de linha final. */
    public List<String> linhas() {
        return List.copyOf(linhas);
    }

    /** Todas as linhas juntas, para procurar valores que nunca podem aparecer. */
    public String todas() {
        return String.join("", linhas);
    }

    public List<JsonNode> eventos() {
        return linhas.stream().map(LogJson::ler).toList();
    }

    public List<JsonNode> doLogger(Class<?> classe) {
        return eventos().stream().filter(evento -> classe.getName().equals(evento.path("logger").asText())).toList();
    }

    /** Eventos de chamada externa com esta operação, pela ordem em que foram escritos. */
    public List<JsonNode> chamadas(String operacao) {
        return eventos().stream().filter(evento -> evento.has("sistema") && operacao.equals(evento.path("operacao").asText()))
                .toList();
    }

    public JsonNode ultimaChamada(String operacao) {
        List<JsonNode> chamadas = chamadas(operacao);
        if (chamadas.isEmpty()) {
            throw new AssertionError("Sem evento de chamada externa com operacao=" + operacao + " em: " + todas());
        }
        return chamadas.getLast();
    }

    public static JsonNode ler(String linha) {
        try {
            return JSON.readTree(linha);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Linha de log que não é JSON: " + linha, e);
        }
    }

    public static final class Extensao implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        private static final ExtensionContext.Namespace ESPACO = ExtensionContext.Namespace.create(LogJson.class);

        @Override
        public void beforeEach(ExtensionContext contexto) {
            contexto.getStore(ESPACO).put(LogJson.class, LogJson.capturar());
        }

        @Override
        public void afterEach(ExtensionContext contexto) {
            LogJson log = contexto.getStore(ESPACO).remove(LogJson.class, LogJson.class);
            if (log != null) {
                log.parar();
            }
        }

        @Override
        public boolean supportsParameter(ParameterContext parametro, ExtensionContext contexto) {
            return parametro.getParameter().getType() == LogJson.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parametro, ExtensionContext contexto) {
            return contexto.getStore(ESPACO).get(LogJson.class, LogJson.class);
        }
    }
}
