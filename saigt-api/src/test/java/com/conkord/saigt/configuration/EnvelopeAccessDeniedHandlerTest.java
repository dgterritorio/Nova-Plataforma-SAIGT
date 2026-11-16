package com.conkord.saigt.configuration;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EnvelopeAccessDeniedHandler handler = new EnvelopeAccessDeniedHandler(objectMapper);

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    void deveEscrever403ComEnvelopeETraceIdDoMdc() throws Exception {
        MDC.put("apiTraceId", "trace-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("sem permissão"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("SEM_PERMISSAO");
        assertThat(body.traceId()).isEqualTo("trace-456");
        assertThat(body.campos()).isNull();
    }

    @Test
    void deveUsarAcaoNaoAutorizadaNasInterfacesDeIntegracao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/integracoes/snit/igt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("outro sistema"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("ACAO_NAO_AUTORIZADA");
    }

    @Test
    void deveEscreverEmUtf8ComAMensagemAcentuadaIntacta() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("sem permissão"));

        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("\"mensagem\":\"Ação não autorizada.\"");
    }
}
