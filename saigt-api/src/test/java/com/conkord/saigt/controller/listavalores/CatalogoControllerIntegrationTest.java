package com.conkord.saigt.controller.listavalores;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoControllerIntegrationTest {

    private static final String URL = "/api/v1/catalogos";

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor comoInterno(String perfilInterno) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfilInterno));
    }

    private RequestPostProcessor comoEntidade() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", java.util.Map.of("nif", "999888777", "nome", "Entidade Teste", "pcm", false)));
    }

    @Test
    void tecnicoNaoDeveConseguirListarOsCatalogos() throws Exception {
        mockMvc.perform(get(URL).with(comoInterno("TECNICO"))).andExpect(status().isForbidden());
    }

    @Test
    void masterDeveConseguirListarOsCatalogos() throws Exception {
        mockMvc.perform(get(URL).with(comoInterno("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Tipos IGT"))
                .andExpect(jsonPath("$[1].titulo").value("Tipos de procedimento"))
                .andExpect(jsonPath("$[2].titulo").value("Nomes de documento"))
                .andExpect(jsonPath("$[3].titulo").value("NUTs (região)"));
    }

    @Test
    void utilizadorExternoNaoDeveConseguirConsultar() throws Exception {
        mockMvc.perform(get(URL).with(comoEntidade())).andExpect(status().isForbidden());
    }

    @Test
    void semTokenDeveReceber401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
