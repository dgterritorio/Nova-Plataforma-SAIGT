package com.conkord.saigt.integracao.core;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.regex.Pattern;

public class PropagacaoCorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    static final String CABECALHO = "X-Trace-Id";
    private static final String MDC_CORRELATION_ID = "apiTraceId";
    private static final Pattern VALOR_VALIDO = Pattern.compile("[!-~]{1,128}");

    @Override
    public ClientHttpResponse intercept(HttpRequest pedido, byte[] corpo, ClientHttpRequestExecution execucao)
            throws IOException {
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null && VALOR_VALIDO.matcher(correlationId).matches()) {
            pedido.getHeaders().set(CABECALHO, correlationId);
        }
        return execucao.execute(pedido, corpo);
    }
}
