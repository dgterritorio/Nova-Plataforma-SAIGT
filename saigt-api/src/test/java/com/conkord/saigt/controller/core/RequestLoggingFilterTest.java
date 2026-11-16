package com.conkord.saigt.controller.core;

import com.conkord.saigt.configuration.EnvelopeAuthenticationEntryPoint;
import com.conkord.saigt.configuration.LogJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LogJson.Extensao.class)
class RequestLoggingFilterTest {

    private static final Logger LOG_DO_PEDIDO = LoggerFactory.getLogger("teste.pedido");

    /** Corre o pedido pela mesma ordem da aplicação: TraceIdFilter, RequestLoggingFilter e o tratamento. */
    private static MockHttpServletResponse tratar(MockHttpServletRequest pedido, int estado) throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                LOG_DO_PEDIDO.info("evento do tratamento");
                resp.setStatus(estado);
            }
        };
        new MockFilterChain(servlet, new TraceIdFilter(), new RequestLoggingFilter()).doFilter(pedido, resposta);
        return resposta;
    }

    private static JsonNode eventoDoPedido(LogJson log) {
        return log.doLogger(RequestLoggingFilter.class).getLast();
    }

    @Test
    void deveMascararOTicketDeDescargaNoCaminhoRegistado() {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/descargas/segredo.assinatura");

        assertThat(RequestLoggingFilter.caminho(pedido)).isEqualTo("/api/v1/descargas/***");
    }

    @Test
    void deveManterOsOutrosCaminhos() {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/processos/1/documentos");

        assertThat(RequestLoggingFilter.caminho(pedido)).isEqualTo("/api/v1/processos/1/documentos");
    }

    @Test
    void deveEscreverOMetodoOCaminhoOEstadoEADuracaoEmCampos(LogJson log) throws Exception {
        tratar(new MockHttpServletRequest("POST", "/api/v1/processos/1/documentos"), 201);

        JsonNode evento = eventoDoPedido(log);
        assertThat(evento.path("level").asText()).isEqualTo("INFO");
        assertThat(evento.path("metodo").asText()).isEqualTo("POST");
        assertThat(evento.path("caminho").asText()).isEqualTo("/api/v1/processos/1/documentos");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(201);
        assertThat(evento.path("duracaoMs").isIntegralNumber()).isTrue();
        assertThat(evento.path("duracaoMs").asLong()).isNotNegative();
    }

    @Test
    void deveEscreverUmSoEventoPorPedidoComOFiltroNoServletENaCadeiaDeSeguranca(LogJson log) throws Exception {
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(404);
            }
        };

        new MockFilterChain(servlet, new TraceIdFilter(), new RequestLoggingFilter(), new RequestLoggingFilter())
                .doFilter(new MockHttpServletRequest("GET", "/api/v1/inexistente"), new MockHttpServletResponse());

        assertThat(log.doLogger(RequestLoggingFilter.class)).singleElement()
                .satisfies(evento -> assertThat(evento.path("estadoHttp").asInt()).isEqualTo(404));
    }

    @Test
    void naoDeveEscreverOTicketDeDescargaEmNenhumCampo(LogJson log) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/descargas/ticket-segredo-7f3.assinatura");
        pedido.setQueryString("ticket=ticket-query-8e1");

        tratar(pedido, 200);

        assertThat(eventoDoPedido(log).path("caminho").asText()).isEqualTo("/api/v1/descargas/***");
        assertThat(log.todas()).doesNotContain("ticket-segredo-7f3").doesNotContain("ticket-query-8e1");
    }

    @Test
    void naoDeveEscreverOTokenDoLinkPublicoNemAQueryStringEmNenhumCampo(LogJson log) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/publico/token-segredo-4b2/Regulamento.pdf");
        pedido.setQueryString("token=token-query-9c4");

        tratar(pedido, 200);

        assertThat(eventoDoPedido(log).path("caminho").asText()).isEqualTo("/api/v1/publico/***/Regulamento.pdf");
        assertThat(log.todas()).doesNotContain("token-segredo-4b2").doesNotContain("token-query-9c4");
    }

    @Test
    void deveDarOCorrelationIdDoCabecalhoATodosOsEventosDoPedidoEDevolveLoNaResposta(LogJson log) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/processos");
        pedido.addHeader("X-Trace-Id", "abc-123");

        MockHttpServletResponse resposta = tratar(pedido, 200);

        assertThat(resposta.getHeader("X-Trace-Id")).isEqualTo("abc-123");
        List<JsonNode> eventos = log.eventos();
        assertThat(eventos).hasSize(2)
                .allSatisfy(evento -> assertThat(evento.path("correlationId").asText()).isEqualTo("abc-123"));
        assertThat(eventos).noneSatisfy(evento -> assertThat(evento.has("apiTraceId")).isTrue());
    }

    @Test
    void deveGerarUmCorrelationIdIgualEmTodosOsEventosDoPedidoENaResposta(LogJson log) throws Exception {
        MockHttpServletResponse resposta = tratar(new MockHttpServletRequest("GET", "/api/v1/processos"), 200);

        String gerado = resposta.getHeader("X-Trace-Id");
        assertThat(gerado).isNotBlank();
        assertThat(log.eventos()).hasSize(2)
                .allSatisfy(evento -> assertThat(evento.path("correlationId").asText()).isEqualTo(gerado));
    }

    @Test
    void deveUsarOCorrelationIdGeradoNoTraceIdDoEnvelopeDeErroENosEventosDoPedido(LogJson log) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                LOG_DO_PEDIDO.warn("pedido recusado");
                new EnvelopeAuthenticationEntryPoint(objectMapper).commence(req, resp, new BadCredentialsException("x"));
            }
        };
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        new MockFilterChain(servlet, new TraceIdFilter(), new RequestLoggingFilter())
                .doFilter(new MockHttpServletRequest("GET", "/api/v1/processos"), resposta);

        String traceIdDoEnvelope = objectMapper.readTree(resposta.getContentAsString()).path("traceId").asText();
        assertThat(traceIdDoEnvelope).isNotBlank().isNotEqualTo("unknown").isEqualTo(resposta.getHeader("X-Trace-Id"));
        assertThat(log.eventos()).hasSize(2)
                .allSatisfy(evento -> assertThat(evento.path("correlationId").asText()).isEqualTo(traceIdDoEnvelope));
        assertThat(eventoDoPedido(log).path("estadoHttp").asInt()).isEqualTo(401);
    }
}
