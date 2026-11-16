package com.conkord.saigt.configuration;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limites próprios forçam um contexto Spring isolado, sem esgotar o RateLimiterRegistry de outras classes. */
@SpringBootTest(properties = {
        "app.security.auth-disabled=false",
        "resilience4j.ratelimiter.configs.api.limit-for-period=3",
        "resilience4j.ratelimiter.configs.api.limit-refresh-period=1m"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final String URL_PERFIS = "/api/v1/sessao/me/permissoes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pedidoQueExcedeOLimiteDoUtilizadorRecebe429ComEnvelopeETraceId() throws Exception {
        String sub = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(URL_PERFIS).with(jwtComSub(sub))).andExpect(status().isOk());
        }

        MvcResult resultado = mockMvc.perform(get(URL_PERFIS).with(jwtComSub(sub)))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(resultado.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("LIMITE_REQUISICOES_EXCEDIDO");
        String traceIdHeader = resultado.getResponse().getHeader("X-Trace-Id");
        assertThat(traceIdHeader).isNotBlank();
        assertThat(body.traceId()).isEqualTo(traceIdHeader);
    }

    @Test
    void utilizadorDiferenteContinuaAServidoAposOutroExcederOSeuLimite() throws Exception {
        String subA = UUID.randomUUID().toString();
        String subB = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(URL_PERFIS).with(jwtComSub(subA))).andExpect(status().isOk());
        }
        mockMvc.perform(get(URL_PERFIS).with(jwtComSub(subA))).andExpect(status().isTooManyRequests());

        mockMvc.perform(get(URL_PERFIS).with(jwtComSub(subB))).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComSub(String sub) {
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("perfil", "CONSULTA_GERAL"));
    }
}
