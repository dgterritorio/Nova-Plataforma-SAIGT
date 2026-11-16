package com.conkord.saigt.controller.classificacaosrup;

import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tabela é partilhada, sem rollback, por toda a suite: cada teste cria Domínios com um código único e só
 * olha para eles.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassificacaoSrupControllerIntegrationTest {

    private static final String URL = "/api/v1/classificacao-srup";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ListaValoresRepository listaValoresRepository;

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ListaValores criar(String tipo, String descricao, int ordem, ListaValores pai) {
        return listaValoresRepository.saveAndFlush(new ListaValores(tipo, "X" + sufixo(), descricao, ordem, pai));
    }

    private RequestPostProcessor comoUtilizadorDeUmaCamara() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", java.util.Map.of("nif", "501305875", "nome", "Câmara Municipal de Sintra", "pcm", false)));
    }

    private RequestPostProcessor comoMaster() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    @Test
    void deveDevolverOsDominiosComClassesETipologiasAtivasPorOrdemAQualquerUtilizadorAutenticado() throws Exception {
        String marca = sufixo();
        ListaValores dominio = criar("DOMINIO_SRUP", "Dominio " + marca, 1, null);
        ListaValores classe = criar("CLASSE_SRUP", "Classe " + marca, 1, dominio);
        criar("TIPOLOGIA_SRUP", "Segunda " + marca, 2, classe);
        criar("TIPOLOGIA_SRUP", "Primeira " + marca, 1, classe);
        ListaValores inativa = criar("TIPOLOGIA_SRUP", "Inativa " + marca, 3, classe);
        inativa.setAtivo(false);
        listaValoresRepository.saveAndFlush(inativa);

        mockMvc.perform(get(URL).with(comoUtilizadorDeUmaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio " + marca + "')].classes[0].descricao")
                        .value(contains("Classe " + marca)))
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio " + marca + "')].classes[0].tipologias[*].descricao")
                        .value(contains("Primeira " + marca, "Segunda " + marca)));
    }

    @Test
    void naoDeveDevolverClassesSemTipologiasAtivasNemDominiosSemEssasClasses() throws Exception {
        String marca = sufixo();
        ListaValores dominioVazio = criar("DOMINIO_SRUP", "Dominio vazio " + marca, 1, null);
        criar("CLASSE_SRUP", "Classe sem tipologias " + marca, 1, dominioVazio);
        ListaValores dominioMisto = criar("DOMINIO_SRUP", "Dominio misto " + marca, 2, null);
        criar("CLASSE_SRUP", "Classe vazia " + marca, 1, dominioMisto);
        ListaValores classeComTipologia = criar("CLASSE_SRUP", "Classe cheia " + marca, 2, dominioMisto);
        criar("TIPOLOGIA_SRUP", "Tipologia " + marca, 1, classeComTipologia);

        mockMvc.perform(get(URL).with(comoUtilizadorDeUmaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio vazio " + marca + "')]").value(empty()))
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio misto " + marca + "')].classes[*].descricao")
                        .value(contains("Classe cheia " + marca)));
    }

    @Test
    void deveMostrarAClasseDepoisDeOMasterCriarLheUmaTipologiaAtiva() throws Exception {
        String marca = sufixo();
        ListaValores dominio = criar("DOMINIO_SRUP", "Dominio novo " + marca, 1, null);
        ListaValores classe = criar("CLASSE_SRUP", "Classe nova " + marca, 1, dominio);

        mockMvc.perform(get(URL).with(comoUtilizadorDeUmaCamara()))
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio novo " + marca + "')]").value(empty()));

        mockMvc.perform(post("/api/v1/lista-valores").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"TIPOLOGIA_SRUP\",\"codigo\":\"N" + marca + "\",\"descricao\":\"Pedreira "
                                + marca + "\",\"ordem\":75,\"paiId\":" + classe.getId() + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(comoUtilizadorDeUmaCamara()))
                .andExpect(jsonPath("$[?(@.descricao == 'Dominio novo " + marca + "')].classes[0].tipologias[0].descricao")
                        .value(contains("Pedreira " + marca)));
    }

    @Test
    void semTokenDeveReceber401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
