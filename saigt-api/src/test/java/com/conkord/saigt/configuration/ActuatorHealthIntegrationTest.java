package com.conkord.saigt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O que o Actuator mostra a pedidos anónimos: só o estado agregado do health. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveDevolverSoStatusUpQuandoHealthAnonimo() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void deveDevolver401QuandoPrometheusAnonimo() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void naoDeveExporEnvQuandoAnonimo() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/actuator/env")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isIn(401, 404);
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("propertySources");
    }
}
