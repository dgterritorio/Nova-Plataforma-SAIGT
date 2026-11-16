package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `PATCH /processos/{id}/enviar-incm` de ponta a ponta (autorização, montagem, cliente e gravação) num Procedimento
 * configurado com `serie_dr = PRIMEIRA`: não há seed de 1.ª série, a configuração nasce no teste.
 */
@SpringBootTest(properties = {
        "app.security.auth-disabled=false",
        "app.incm.service-key=" + EnvioIncmPrimeiraSerieIntegrationTest.SERVICE_KEY,
        "app.incm.read-timeout=2s",
        "app.publico.base-url=https://saigt.teste"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnvioIncmPrimeiraSerieIntegrationTest extends EnvioIncmTesteBase {

    static final String SERVICE_KEY = "chave-secreta-primeira-serie";

    private static final String SUBMIT_NEW_ACT = "/SubmitNewAct";
    private static final long ACT_TYPE_ID_SERIE_1 = 3L;
    private static final ServidorIncmTeste INCM = ServidorIncmTeste.iniciar();

    @DynamicPropertySource
    static void urlIncm(DynamicPropertyRegistry registry) {
        registry.add("app.incm.base-url", INCM::url);
    }

    @AfterAll
    static void pararIncm() {
        INCM.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @BeforeEach
    void limparPedidos() {
        INCM.pedidos().clear();
        INCM.responder(SUBMIT_NEW_ACT, 200, """
                {"IsSuccess": true, "ErrorMessageList": [], "ActIdentifier": 900000000000001}""");
    }

    @Test
    void deveEnviarSoADiarioRepublicaSeries1ActSemSuplementoEFicarEmValidacaoIncm() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(false, null);

        enviarComoDgt(cenario).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));

        JsonNode dgtAct = dgtActRecebido();
        assertThat(nomes(dgtAct)).containsExactlyInAnyOrder("UserEnrollmentAgreementIdentifier",
                "DiarioRepublicaSeries1Act", "ImageURLList", "FileURLList");
        JsonNode serie1 = dgtAct.get("DiarioRepublicaSeries1Act");
        assertThat(nomes(serie1)).containsExactlyInAnyOrder("ActTypeId", "PublishInSuplement", "ActSummary",
                "SignatureDate");
        assertThat(serie1.get("ActTypeId").asLong()).isEqualTo(ACT_TYPE_ID_SERIE_1);
        assertThat(serie1.get("PublishInSuplement").isBoolean()).isTrue();
        assertThat(serie1.get("PublishInSuplement").asBoolean()).isFalse();
        assertThat(serie1.get("ActSummary").asText()).isEqualTo("Resolução que ratifica o plano");
        assertThat(serie1.get("SignatureDate").asText()).isEqualTo("2026-10-20");
        assertThat(dgtAct.get("FileURLList")).hasSize(1);
        Processo processo = recarregar(cenario.processo());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(processo.getIncmActIdentifier()).isEqualTo("900000000000001");
    }

    @Test
    void deveEnviarPublishInSuplementEADataDoSuplementoEmFormatoIso() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(true, LocalDate.of(2026, 12, 4));

        enviarComoDgt(cenario).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));

        JsonNode serie1 = dgtActRecebido().get("DiarioRepublicaSeries1Act");
        assertThat(serie1.get("PublishInSuplement").asBoolean()).isTrue();
        assertThat(serie1.get("SuplementPublishingDate").asText()).isEqualTo("2026-12-04");
    }

    @Test
    void deveGravarAComunicacaoComASerieDrPrimeiraEOPedidoSemOServiceKey() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(true, LocalDate.of(2026, 12, 4));

        enviarComoDgt(cenario).andExpect(status().isOk());

        assertThat(INCM.pedidos()).singleElement()
                .satisfies(pedido -> assertThat(pedido.cabecalhos().getFirst("ServiceKey")).isEqualTo(SERVICE_KEY));
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getOperacao()).isEqualTo("SUBMIT_NEW_ACT");
        assertThat(linha.getSerieDr()).isEqualTo("PRIMEIRA");
        assertThat(linha.getActIdentifier()).isEqualTo(900000000000001L);
        assertThat(linha.getTransicaoDerivada()).isEqualTo("EM_VALIDACAO_INCM");
        assertThat(linha.getPedido()).doesNotContain(SERVICE_KEY).doesNotContain("ServiceKey");
        JsonNode gravado = objectMapper.readTree(linha.getPedido()).get("DGTAct");
        assertThat(gravado.has("DiarioRepublicaSeries2Act")).isFalse();
        assertThat(gravado.get("DiarioRepublicaSeries1Act").get("SuplementPublishingDate").asText())
                .isEqualTo("2026-12-04");
    }

    @Test
    void deveDevolver400NoCampoDataSuplementoSemChamarAIncmQuandoOSuplementoNaoTemData() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(true, null);

        enviarComoDgt(cenario).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("dataSuplemento"));

        verificarQueAIncmNaoFoiChamada(cenario);
    }

    @Test
    void deveDevolver400NoCampoTipoAtoSemChamarAIncmQuandoOTipoNaoTemActTypeIdDaPrimeiraSerie() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(false, null);
        jdbcTemplate.update("UPDATE tipo_ato SET incm_act_type_id_serie_1 = NULL, incm_act_type_id_serie_2 = 10 WHERE id = "
                + "(SELECT tipo_ato_id FROM processo WHERE id = ?)", cenario.processo().getId());

        enviarComoDgt(cenario).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("tipoAto"));

        verificarQueAIncmNaoFoiChamada(cenario);
    }

    @Test
    void deveDevolver400NoCampoPublicarSuplementoSemChamarAIncmQuandoNaoEstaPreenchido() throws Exception {
        Cenario cenario = cenarioPrimeiraSerie(null, null);

        enviarComoDgt(cenario).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("publicarSuplemento"));

        verificarQueAIncmNaoFoiChamada(cenario);
    }

    @Test
    void deveEnviarNaSegundaSerieComoAntesSoComADiarioRepublicaSeries2Act() throws Exception {
        Cenario cenario = cenarioValidado("SEGUNDA_H", "Aviso de início");

        enviarComoDgt(cenario).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));

        JsonNode dgtAct = dgtActRecebido();
        assertThat(dgtAct.has("DiarioRepublicaSeries1Act")).isFalse();
        assertThat(nomes(dgtAct.get("DiarioRepublicaSeries2Act"))).containsExactlyInAnyOrder("ActTypeId", "PartId",
                "InformationTypeIdentifier", "ActSummary", "SignatureDate", "LegalProvision");
        assertThat(comunicacoes(cenario.processo()).get(0).getSerieDr()).isEqualTo("SEGUNDA_H");
    }

    @Test
    void deveDevolver400NosCamposDaSegundaSerieSemChamarAIncmComoAntes() throws Exception {
        Cenario cenario = cenarioValidado("SEGUNDA_C", "Aviso de início");
        Processo processo = recarregar(cenario.processo());
        processo.setNormaLegal(null);
        processo.setPublicarSuplemento(true);
        processoRepository.saveAndFlush(processo);

        enviarComoDgt(cenario).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("normaLegal"));

        verificarQueAIncmNaoFoiChamada(cenario);
    }

    /** Em `VALIDADO_DGT` a DGT já validou os documentos: com algum por validar, o envio dá 409. */
    private Cenario cenarioValidado(String serieDr, String sumario) {
        Cenario cenario = criarCenario(serieDr, sumario);
        jdbcTemplate.update("UPDATE processo_documento SET estado_validacao = 'VALIDADO' WHERE processo_id = ?",
                cenario.processo().getId());
        return cenario;
    }

    /** Sem os campos só da 2.ª série: a 1.ª não os exige. */
    private Cenario cenarioPrimeiraSerie(Boolean publicarSuplemento, LocalDate dataSuplemento) {
        Cenario cenario = cenarioValidado("PRIMEIRA", "Resolução que ratifica o plano");
        jdbcTemplate.update("UPDATE tipo_ato SET incm_act_type_id_serie_1 = ?, incm_act_type_id_serie_2 = NULL "
                + "WHERE id = (SELECT tipo_ato_id FROM processo WHERE id = ?)", ACT_TYPE_ID_SERIE_1,
                cenario.processo().getId());
        Processo processo = recarregar(cenario.processo());
        processo.setDrParteId(null);
        processo.setNormaLegal(null);
        processo.setTipoInformacao(null);
        processo.setPublicarSuplemento(publicarSuplemento);
        processo.setDataSuplemento(dataSuplemento);
        processoRepository.saveAndFlush(processo);
        return cenario;
    }

    private ResultActions enviarComoDgt(Cenario cenario) throws Exception {
        UUID processoId = cenario.processo().getId();
        Utilizador novo = new Utilizador("Técnico DGT", "tecnico." + UUID.randomUUID() + "@dgt.invalid", "AD_DGT");
        novo.setPerfilInterno(PerfilInterno.TECNICO);
        Utilizador tecnico = utilizadorRepository.saveAndFlush(novo);
        return mockMvc.perform(patch("/api/v1/processos/" + processoId + "/enviar-incm")
                .with(jwt().jwt(builder -> builder.claim("sub", tecnico.getId().toString()).claim("origem", "AD_DGT")
                        .claim("perfil_interno", "TECNICO")))
                .contentType("application/json")
                .content("{\"versao\":" + recarregar(cenario.processo()).getVersao() + "}"));
    }

    private JsonNode dgtActRecebido() throws Exception {
        assertThat(INCM.pedidos()).hasSize(1);
        return objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
    }

    private void verificarQueAIncmNaoFoiChamada(Cenario cenario) {
        assertThat(INCM.pedidos()).isEmpty();
        assertThat(comunicacoes(cenario.processo())).isEmpty();
        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(linksAtivos(cenario.publicacao())).isEmpty();
    }

    private static Iterable<String> nomes(JsonNode no) {
        return no.properties().stream().map(Map.Entry::getKey).toList();
    }
}
