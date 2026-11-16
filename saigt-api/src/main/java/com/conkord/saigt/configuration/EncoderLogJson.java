package com.conkord.saigt.configuration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.JsonProviders;
import net.logstash.logback.composite.loggingevent.KeyValuePairsJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.composite.loggingevent.ThrowableClassNameJsonProvider;
import net.logstash.logback.composite.loggingevent.ThrowableMessageJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;

import java.util.List;

/**
 * Log técnico em JSON, um evento por linha (TR-258). Do MDC só saem as chaves da lista: `apiTraceId` como
 * `correlationId` (TR-132), `traceId` e `spanId` do Micrometer e `execucaoJob` dos jobs agendados. Os campos de cada
 * evento vêm dos pares chave-valor do SLF4J (`addKeyValue`). Usado pelo `logback-spring.xml` e pelos testes.
 */
public class EncoderLogJson extends LoggingEventCompositeJsonEncoder {

    public static final String MDC_CORRELATION_ID = "apiTraceId";
    public static final String MDC_EXECUCAO_JOB = "execucaoJob";
    private static final int PROFUNDIDADE_MAXIMA_STACK = 60;

    @Override
    public void start() {
        JsonProviders<ILoggingEvent> providers = getProviders();

        LoggingEventFormattedTimestampJsonProvider instante = new LoggingEventFormattedTimestampJsonProvider();
        instante.setFieldName("@timestamp");
        instante.setTimeZone("UTC");
        providers.addProvider(instante);

        LogLevelJsonProvider nivel = new LogLevelJsonProvider();
        nivel.setFieldName("level");
        providers.addProvider(nivel);

        LoggerNameJsonProvider logger = new LoggerNameJsonProvider();
        logger.setFieldName("logger");
        providers.addProvider(logger);

        LoggingEventThreadNameJsonProvider thread = new LoggingEventThreadNameJsonProvider();
        thread.setFieldName("thread");
        providers.addProvider(thread);

        MessageJsonProvider mensagem = new MessageJsonProvider();
        mensagem.setFieldName("message");
        providers.addProvider(mensagem);

        MdcJsonProvider mdc = new MdcJsonProvider();
        mdc.setIncludeMdcKeyNames(List.of(MDC_CORRELATION_ID, "traceId", "spanId", MDC_EXECUCAO_JOB));
        mdc.addMdcKeyFieldName(MDC_CORRELATION_ID + "=correlationId");
        providers.addProvider(mdc);

        providers.addProvider(new KeyValuePairsJsonProvider());

        ThrowableClassNameJsonProvider classeExcecao = new ThrowableClassNameJsonProvider();
        classeExcecao.setFieldName("exceptionClass");
        classeExcecao.setUseSimpleClassName(false);
        providers.addProvider(classeExcecao);

        ThrowableMessageJsonProvider mensagemExcecao = new ThrowableMessageJsonProvider();
        mensagemExcecao.setFieldName("exceptionMessage");
        providers.addProvider(mensagemExcecao);

        ShortenedThrowableConverter conversor = new ShortenedThrowableConverter();
        conversor.setMaxDepthPerThrowable(PROFUNDIDADE_MAXIMA_STACK);
        StackTraceJsonProvider stackTrace = new StackTraceJsonProvider();
        stackTrace.setFieldName("stackTrace");
        stackTrace.setThrowableConverter(conversor);
        providers.addProvider(stackTrace);

        setLineSeparator("UNIX");
        super.start();
    }
}
