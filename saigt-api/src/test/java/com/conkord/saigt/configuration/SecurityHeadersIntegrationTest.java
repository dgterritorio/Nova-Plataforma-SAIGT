package com.conkord.saigt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

    private static final String URL = "/api/v1/sessao/me/permissoes";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveIncluirCspHstsXFrameOptionsEXContentTypeOptionsQuandoTls() throws Exception {
        mockMvc.perform(get(URL).secure(true).with(jwtComPerfilConsultaGeral()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-Content-Type-Options"));
    }

    @Test
    void naoDeveIncluirHstsQuandoPedidoNaoChegaPorTls() throws Exception {
        mockMvc.perform(get(URL).secure(false).with(jwtComPerfilConsultaGeral()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComPerfilConsultaGeral() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("perfil", "CONSULTA_GERAL"));
    }
}
