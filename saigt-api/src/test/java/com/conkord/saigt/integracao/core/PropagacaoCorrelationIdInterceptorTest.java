package com.conkord.saigt.integracao.core;

import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PropagacaoCorrelationIdInterceptorTest {

    private ServidorIncmTeste servidor;
    private RestClient restClient;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        servidor.responder("/recursos", 200, "{}");
        restClient = RestClient.builder()
                .baseUrl(servidor.url())
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor())
                .build();
    }

    @AfterEach
    void parar() {
        servidor.close();
        MDC.remove("apiTraceId");
    }

    private String traceIdRecebido() {
        restClient.get().uri("/recursos").retrieve().toBodilessEntity();
        return servidor.pedidos().getFirst().cabecalhos().getFirst("X-Trace-Id");
    }

    @Test
    void deveAcrescentarOXTraceIdComOValorDoMdc() {
        MDC.put("apiTraceId", "b3f1c2d4-trace-157");

        assertThat(traceIdRecebido()).isEqualTo("b3f1c2d4-trace-157");
    }

    @Test
    void naoDeveAcrescentarOXTraceIdSemValorNoMdc() {
        assertThat(traceIdRecebido()).isNull();
    }

    @Test
    void naoDeveAcrescentarUmValorComCaracteresQueOCabecalhoNaoAceita() {
        MDC.put("apiTraceId", "trace com espaços");

        assertThat(traceIdRecebido()).isNull();
    }
}
