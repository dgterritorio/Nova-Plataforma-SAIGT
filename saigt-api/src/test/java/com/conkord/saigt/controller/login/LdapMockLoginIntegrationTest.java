package com.conkord.saigt.controller.login;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa o {@code StubLdapAutenticador} real (não um mock), ativado por `app.ldap-mock.enabled=true`. */
@SpringBootTest(properties = "app.ldap-mock.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LdapMockLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void oGrantAdPasswordAutenticaPeloUsernameDoMockECriaAContaVisitante() throws Exception {
        String corpo = mockMvc.perform(post("/oauth2/token").contentType(APPLICATION_FORM_URLENCODED)
                        .content("grant_type=urn%3Asaigt%3Aparams%3Aoauth%3Agrant-type%3Aad-password&client_id=saigt-ui"
                                + "&username=carlos.tecnico&password=Senha123%21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(corpo, "$.access_token");
        assertThat(SignedJWT.parse(accessToken).getJWTClaimsSet().getStringClaim("origem")).isEqualTo("AD_DGT");
        Utilizador conta = utilizadorRepository
                .findByNomeAndOrigemAutenticacao("carlos.tecnico", "AD_DGT").orElseThrow();
        assertThat(conta.getPerfilInterno()).isEqualTo(PerfilInterno.VISITANTE);
    }

    @Test
    void oGrantAdPasswordRecusaOEmailEAPasswordErradaDoMock() throws Exception {
        mockMvc.perform(post("/oauth2/token").contentType(APPLICATION_FORM_URLENCODED)
                        .content("grant_type=urn%3Asaigt%3Aparams%3Aoauth%3Agrant-type%3Aad-password&client_id=saigt-ui"
                                + "&username=carlos.tecnico%40dgt.pt&password=Senha123%21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        mockMvc.perform(post("/oauth2/token").contentType(APPLICATION_FORM_URLENCODED)
                        .content("grant_type=urn%3Asaigt%3Aparams%3Aoauth%3Agrant-type%3Aad-password&client_id=saigt-ui"
                                + "&username=carlos.tecnico&password=errada"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }
}
