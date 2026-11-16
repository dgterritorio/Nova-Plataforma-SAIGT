package com.conkord.saigt.controller.listavalores;

import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tabela é partilhada, sem rollback, por toda a suite: cada teste usa um `tipo` único ou, quando o
 * `POST` exige um tipo do catálogo, um `codigo` único.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListaValoresControllerIntegrationTest {

    private static final String URL = "/api/v1/lista-valores";
    private static final String TIPO_DO_CATALOGO = TipoListaValores.CATEGORIA_REJEICAO_DGT.name();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ListaValoresRepository listaValoresRepository;

    private String novoTipo() {
        return "TESTE_TIPO_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String novoCodigo() {
        return "TESTE_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ListaValores criarItem(String tipo, String codigo) {
        return listaValoresRepository.saveAndFlush(new ListaValores(tipo, codigo, "Descrição inicial", 1));
    }

    private RequestPostProcessor comoAdmin() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private RequestPostProcessor comoTecnico() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    @Test
    void tecnicoDeveConseguirListarPorTipo() throws Exception {
        String tipo = novoTipo();
        criarItem(tipo, "A");

        mockMvc.perform(get(URL).param("tipo", tipo).with(comoTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value(tipo))
                .andExpect(jsonPath("$[0].codigo").value("A"));
    }

    @Test
    void adminDeveConseguirCriarItemNumTipoExistente() throws Exception {
        String codigo = novoCodigo();

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"" + TIPO_DO_CATALOGO + "\",\"codigo\":\"" + codigo
                                + "\",\"descricao\":\"Novo item\",\"ordem\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value(TIPO_DO_CATALOGO))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void tecnicoNaoDeveConseguirCriarItem() throws Exception {
        String tipo = novoTipo();
        criarItem(tipo, "A");

        mockMvc.perform(post(URL).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"" + tipo + "\",\"codigo\":\"B\",\"descricao\":\"Novo item\",\"ordem\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminNaoDeveConseguirCriarItemNumTipoInexistente() throws Exception {
        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"TIPO_QUE_NAO_EXISTE_XYZ\",\"codigo\":\"B\",\"descricao\":\"Novo item\",\"ordem\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminNaoDeveConseguirCriarComCodigoDuplicadoNoMesmoTipo() throws Exception {
        String codigo = novoCodigo();
        criarItem(TIPO_DO_CATALOGO, codigo);

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"" + TIPO_DO_CATALOGO + "\",\"codigo\":\"" + codigo
                                + "\",\"descricao\":\"Duplicado\",\"ordem\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminDeveConseguirDesativarUmItemSemODeletarFisicamente() throws Exception {
        String tipo = novoTipo();
        ListaValores item = criarItem(tipo, "A");

        mockMvc.perform(patch(URL + "/" + item.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.codigo").value("A"));

        assertThat(listaValoresRepository.findById(item.getId())).isPresent();
    }

    @Test
    void tentarAlterarCodigoNoPatchNaoTemEfeito() throws Exception {
        String tipo = novoTipo();
        ListaValores item = criarItem(tipo, "A");

        mockMvc.perform(patch(URL + "/" + item.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"codigo\":\"OUTRO\",\"descricao\":\"Atualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("A"))
                .andExpect(jsonPath("$.descricao").value("Atualizada"));
    }

    @Test
    void semTokenDeveReceber401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ListaValores criarFilho(String tipo, ListaValores pai) {
        return listaValoresRepository.saveAndFlush(new ListaValores(tipo, novoCodigoCurto(), "Filho", 1, pai));
    }

    private String novoCodigoCurto() {
        return "T" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String corpoComPai(String tipo, String codigo, Long paiId) {
        return "{\"tipo\":\"" + tipo + "\",\"codigo\":\"" + codigo + "\",\"descricao\":\"Pedreira\",\"ordem\":75"
                + (paiId == null ? "" : ",\"paiId\":" + paiId) + "}";
    }

    @Test
    void adminDeveCriarTipologiaComPaiNaClasseEReceberOPaiIdNaResposta() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);
        ListaValores classe = criarFilho("CLASSE_SRUP", dominio);

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("TIPOLOGIA_SRUP", novoCodigoCurto(), classe.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paiId").value(classe.getId()))
                .andExpect(jsonPath("$.tipo").value("TIPOLOGIA_SRUP"));
    }

    @Test
    void adminNaoDeveCriarTipologiaSemPaiNemComPaiDeUmDominio() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("TIPOLOGIA_SRUP", novoCodigoCurto(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("paiId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique o valor de que este depende."));
        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("TIPOLOGIA_SRUP", novoCodigoCurto(), dominio.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("paiId"))
                .andExpect(jsonPath("$.campos[0].erro").value("O valor indicado não é do nível acima."));
    }

    @Test
    void adminNaoDeveCriarValorComPaiNumTipoSemHierarquia() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("MOTIVO_REJEICAO_DGT", novoCodigo(), dominio.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("paiId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este tipo de lista não tem valores dependentes."));
    }

    @Test
    void tecnicoNaoDeveCriarValorComHierarquia() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);
        ListaValores classe = criarFilho("CLASSE_SRUP", dominio);

        mockMvc.perform(post(URL).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("TIPOLOGIA_SRUP", novoCodigoCurto(), classe.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSoDesativaAClasseDepoisDeDesativarAsTipologias() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);
        ListaValores classe = criarFilho("CLASSE_SRUP", dominio);
        ListaValores primeira = criarFilho("TIPOLOGIA_SRUP", classe);
        ListaValores segunda = criarFilho("TIPOLOGIA_SRUP", classe);

        mockMvc.perform(patch(URL + "/" + classe.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isConflict());
        assertThat(listaValoresRepository.findById(classe.getId())).get().extracting(ListaValores::isAtivo).isEqualTo(true);

        for (ListaValores tipologia : new ListaValores[] {primeira, segunda}) {
            mockMvc.perform(patch(URL + "/" + tipologia.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                            .content("{\"ativo\":false}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch(URL + "/" + classe.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void adminNaoDeveAtivarUmaTipologiaCujaClasseEstaInativa() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);
        ListaValores classe = criarFilho("CLASSE_SRUP", dominio);
        ListaValores tipologia = criarFilho("TIPOLOGIA_SRUP", classe);
        tipologia.setAtivo(false);
        classe.setAtivo(false);
        listaValoresRepository.saveAllAndFlush(java.util.List.of(tipologia, classe));

        mockMvc.perform(patch(URL + "/" + tipologia.getId()).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"ativo\":true}"))
                .andExpect(status().isConflict());
    }

    // US-486: a Região e a CIM/AM do formulário SGIFR, geridas pelo Master; a CIM/AM depende da Região.

    @Test
    void adminDeveCriarUmaRegiaoEUmaCimAmComEssaRegiaoComoPai() throws Exception {
        String codigoRegiao = novoCodigoCurto();
        String resposta = mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("REGIAO", codigoRegiao, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("REGIAO"))
                .andExpect(jsonPath("$.paiId").isEmpty())
                .andReturn().getResponse().getContentAsString();
        Long regiaoId = JsonPath.parse(resposta).read("$.id", Long.class);
        String codigoCim = novoCodigoCurto();

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("CIM_AM", codigoCim, regiaoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CIM_AM"))
                .andExpect(jsonPath("$.paiId").value(regiaoId));

        assertThat(listaValoresRepository.findByTipoOrderByOrdemAsc("CIM_AM"))
                .filteredOn(valor -> codigoCim.equals(valor.getCodigo()))
                .singleElement()
                .satisfies(valor -> assertThat(valor.getPai().getId()).isEqualTo(regiaoId));
    }

    @Test
    void adminNaoDeveCriarCimAmSemPaiNemComPaiQueNaoEUmaRegiao() throws Exception {
        ListaValores dominio = criarFilho("DOMINIO_SRUP", null);

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("CIM_AM", novoCodigoCurto(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("paiId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique o valor de que este depende."));
        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("CIM_AM", novoCodigoCurto(), dominio.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("paiId"))
                .andExpect(jsonPath("$.campos[0].erro").value("O valor indicado não é do nível acima."));
    }

    @Test
    void tecnicoLeARegiaoEACimAmMasNaoAsCriaNemAltera() throws Exception {
        ListaValores regiao = criarFilho("REGIAO", null);
        ListaValores cim = criarFilho("CIM_AM", regiao);

        mockMvc.perform(get(URL).param("tipo", "CIM_AM").with(comoTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + cim.getId() + ")].paiId").value(regiao.getId().intValue()));
        mockMvc.perform(post(URL).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("REGIAO", novoCodigoCurto(), null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content(corpoComPai("CIM_AM", novoCodigoCurto(), regiao.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(URL + "/" + cim.getId()).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isForbidden());
        assertThat(listaValoresRepository.findById(cim.getId())).get().extracting(ListaValores::isAtivo).isEqualTo(true);
    }

    @Test
    void regiaoECimAmNaoSaoOpcoesDeFormularioAbertasATodos() throws Exception {
        mockMvc.perform(get(URL + "/opcoes").param("tipo", "REGIAO").with(comoTecnico()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipo"));
        mockMvc.perform(get(URL + "/opcoes").param("tipo", "CIM_AM").with(comoTecnico()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipo"));
    }
}
