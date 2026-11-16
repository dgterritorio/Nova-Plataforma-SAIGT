package com.conkord.saigt.configuration;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EnvelopeAuthenticationEntryPoint entryPoint = new EnvelopeAuthenticationEntryPoint(objectMapper);

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    void deveEscrever401ComEnvelopeETraceIdDoMdc() throws Exception {
        MDC.put("apiTraceId", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("sem token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("NAO_AUTENTICADO");
        assertThat(body.traceId()).isEqualTo("trace-123");
        assertThat(body.campos()).isNull();
    }

    @Test
    void deveEscreverEmUtf8ComAMensagemAcentuadaIntacta() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("sem token"));

        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("\"mensagem\":\"Autenticação necessária.\"");
    }

    @Test
    void deveUsarUnknownQuandoMdcSemTraceId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("sem token"));

        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.traceId()).isEqualTo("unknown");
    }
}
