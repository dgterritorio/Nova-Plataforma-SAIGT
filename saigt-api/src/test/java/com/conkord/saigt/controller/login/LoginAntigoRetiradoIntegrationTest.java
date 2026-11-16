package com.conkord.saigt.controller.login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os tokens só vêm do `POST /oauth2/token` e o JWKS só do `GET /oauth2/jwks`: o login, a escolha de entidade, o
 * refresh, o logout e o JWKS antigos já não existem nem são públicos.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginAntigoRetiradoIntegrationTest {

    private static final List<String> CAMINHOS_RETIRADOS = List.of("/api/v1/auth/login", "/api/v1/auth/login/entidade",
            "/api/v1/auth/refresh", "/api/v1/auth/logout");
    private static final String JWKS_ANTIGO = "/.well-known/jwks.json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void osCaminhosAntigosDeAutenticacaoDevolvem404ComUmTokenValido() throws Exception {
        for (String caminho : CAMINHOS_RETIRADOS) {
            mockMvc.perform(post(caminho).with(jwt().jwt(token -> token.subject("utilizador-ficticio")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ficticio@teste.invalid\",\"password\":\"x\",\"utilizadorInternoDgt\":true}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        }
    }

    @Test
    void osCaminhosAntigosDeAutenticacaoJaNaoSaoPublicos() throws Exception {
        int ip = 1;
        for (String caminho : CAMINHOS_RETIRADOS) {
            // IP próprio por pedido: o limite por IP é partilhado com as outras classes deste contexto.
            mockMvc.perform(post(caminho).header("X-Forwarded-For", "198.51.100." + ip++)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ficticio@teste.invalid\",\"password\":\"x\",\"utilizadorInternoDgt\":true}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        }
    }

    @Test
    void oJwksAntigoJaNaoExisteNemEPublico() throws Exception {
        mockMvc.perform(get(JWKS_ANTIGO).with(jwt().jwt(token -> token.subject("utilizador-ficticio"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(get(JWKS_ANTIGO).header("X-Forwarded-For", "198.51.100.20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }
}
