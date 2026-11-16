package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedido sem JWT é rejeitado pela cadeia de segurança, antes do GlobalExceptionHandler, e devolve o mesmo envelope. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityEnvelopeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveDevolver401ComEnvelopeQuandoSemJwt() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/qualquer-recurso"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        com.conkord.saigt.dto.core.ErrorResponse body = objectMapper.readValue(
                resultado.getResponse().getContentAsString(), com.conkord.saigt.dto.core.ErrorResponse.class);

        assertThat(body.codigo()).isEqualTo("NAO_AUTENTICADO");
        assertThat(body.campos()).isNull();

        String traceIdHeader = resultado.getResponse().getHeader("X-Trace-Id");
        assertThat(traceIdHeader).isNotBlank();
        assertThat(body.traceId()).isEqualTo(traceIdHeader);
    }
}
