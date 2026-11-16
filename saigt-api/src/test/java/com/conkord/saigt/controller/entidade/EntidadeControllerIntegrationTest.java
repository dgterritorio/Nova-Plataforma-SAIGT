package com.conkord.saigt.controller.entidade;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntidadeControllerIntegrationTest {

    private static final String URL = "/api/v1/entidades";
    /** NIPC válido (dígito de controlo módulo 11). */
    private static final String NIPC_VALIDO = "501442600";
    private static final String NIPC_VALIDO_2 = "505187531";
    private static final String DICO_ATIVO = "1111";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private EntidadeContactoRepository entidadeContactoRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private SnitReferenciaRepository snitReferenciaRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @BeforeEach
    void replicaComConcelhoAtivo() {
        if (snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, DICO_ATIVO).isEmpty()) {
            snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, DICO_ATIVO,
                    "Concelho de Teste", "{}", Instant.now()));
        }
    }

    private static RequestPostProcessor comPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor comoMaster() {
        return comPerfil("MASTER");
    }

    private static RequestPostProcessor comoIncm() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("id", 1, "tipo_entidade", "DGT", "nif", NIPC_VALIDO, "nome", "Entidade",
                        "pcm", false)));
    }

    private Entidade gravar(String codigo, TipoEntidade tipo, String descricaoSaigt) {
        return entidadeRepository.saveAndFlush(new Entidade(codigo, tipo, null, "INCM " + descricaoSaigt,
                descricaoSaigt));
    }

    private String json(Map<String, ?> corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private List<Auditoria> auditoria(Long entidadeId, String acao) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("ENTIDADE", entidadeId.toString()).stream()
                .filter(a -> acao.equals(a.getAcao())).toList();
    }

    // ---------- POST ----------

    @Test
    void masterCriaCmSemNipcNemDicoEFicaAuditoriaCriar() throws Exception {
        String corpo = mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9401", "descricaoIncm", "Município Teste",
                                "descricaoSaigt", "CM Teste", "tipoEntidade", "CM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("9401"))
                .andExpect(jsonPath("$.tipoEntidade").value("CM"))
                .andExpect(jsonPath("$.nipc").isEmpty())
                .andExpect(jsonPath("$.dico").isEmpty())
                .andExpect(jsonPath("$.telefone").isEmpty())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.nif").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(corpo).get("id").asLong();
        List<Auditoria> criar = auditoria(id, "CRIAR");
        assertThat(criar).hasSize(1);
        assertThat(criar.get(0).getEntidadeId()).isNull();
        assertThat(objectMapper.readTree(criar.get(0).getDadosAlteracao()).get("codigo").asText()).isEqualTo("9401");
    }

    @Test
    void nipcRepetidoEAceiteECodigoRepetidoDa409() throws Exception {
        entidadeRepository.saveAndFlush(new Entidade("9402", TipoEntidade.CM, NIPC_VALIDO, "Município A", "CM A"));

        mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9403", "descricaoIncm", "Município B",
                                "descricaoSaigt", "CM B", "tipoEntidade", "CM", "nipc", NIPC_VALIDO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nipc").value(NIPC_VALIDO));

        mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9402", "descricaoIncm", "Município C",
                                "descricaoSaigt", "CM C", "tipoEntidade", "CM"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_DUPLICADA"))
                .andExpect(jsonPath("$.mensagem").value("Código de entidade já existe."));
    }

    @Test
    void criarComTipoInvalidoNipcCurtoOuCampoVazioDa400NoCampo() throws Exception {
        mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9404", "descricaoIncm", "X", "descricaoSaigt", "X",
                                "tipoEntidade", "XPTO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoEntidade"))
                .andExpect(jsonPath("$.campos[0].erro").value("Tipo de entidade inválido."));

        mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9404", "descricaoIncm", "X", "descricaoSaigt", "X",
                                "tipoEntidade", "CM", "nipc", "50144260"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("nipc"));

        mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9404", "descricaoIncm", "", "descricaoSaigt", "X",
                                "tipoEntidade", "CM"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("descricaoIncm"));

        assertThat(entidadeRepository.existsByCodigo("9404")).isFalse();
    }

    // ---------- PATCH ----------

    @Test
    void masterCorrigeNipcEDescricaoIncmComAuditoriaDoAntesEDepois() throws Exception {
        Entidade entidade = entidadeRepository.saveAndFlush(
                new Entidade("9405", TipoEntidade.CM, NIPC_VALIDO, "Município Antigo", "CM Antiga"));

        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("nipc", NIPC_VALIDO_2, "descricaoIncm", "Município Novo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nipc").value(NIPC_VALIDO_2))
                .andExpect(jsonPath("$.descricaoIncm").value("Município Novo"));

        List<Auditoria> atualizar = auditoria(entidade.getId(), "ATUALIZAR");
        assertThat(atualizar).hasSize(1);
        JsonNode dados = objectMapper.readTree(atualizar.get(0).getDadosAlteracao());
        assertThat(dados.get("nipc").get("antes").asText()).isEqualTo(NIPC_VALIDO);
        assertThat(dados.get("nipc").get("depois").asText()).isEqualTo(NIPC_VALIDO_2);
        assertThat(dados.get("descricaoIncm").get("antes").asText()).isEqualTo("Município Antigo");
        assertThat(dados.has("codigo")).isFalse();
    }

    @Test
    void patchComOProprioCodigoDa200EComODeOutraDa409() throws Exception {
        gravar("9406", TipoEntidade.CM, "CM Outra");
        Entidade entidade = gravar("9407", TipoEntidade.CM, "CM Propria");

        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9407"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("codigo", "9406"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Código de entidade já existe."));
    }

    @Test
    void dicoAtivoNumaCmDa200EInexistenteOuCurtoDa400() throws Exception {
        Entidade entidade = gravar("9408", TipoEntidade.CM, "CM Dico");

        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("dico", DICO_ATIVO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dico").value(DICO_ATIVO));

        for (String invalido : List.of("9999", "111")) {
            mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                            .content(json(Map.of("dico", invalido))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].path").value("dico"))
                    .andExpect(jsonPath("$.campos[0].erro").value("Concelho inexistente."));
        }
        assertThat(entidadeRepository.findById(entidade.getId()).orElseThrow().getDico()).isEqualTo(DICO_ATIVO);
    }

    @Test
    void dicoNumaCimDa400EMudarCmParaCimLimpaODico() throws Exception {
        Entidade cim = gravar("9409", TipoEntidade.CIM, "CIM Teste");
        Entidade cm = gravar("9410", TipoEntidade.CM, "CM Com Dico");
        cm.setDico(DICO_ATIVO);
        entidadeRepository.saveAndFlush(cm);

        mockMvc.perform(patch(URL + "/" + cim.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("dico", DICO_ATIVO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("dico"));

        mockMvc.perform(patch(URL + "/" + cm.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("tipoEntidade", "CIM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEntidade").value("CIM"))
                .andExpect(jsonPath("$.dico").isEmpty());
    }

    @Test
    void patchSemCamposDa400EIdInexistenteDa404() throws Exception {
        Entidade entidade = gravar("9411", TipoEntidade.CM, "CM Patch");

        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Indique pelo menos um campo a alterar."));
        mockMvc.perform(patch(URL + "/999999999").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("descricaoSaigt", "X"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Entidade não encontrada."));
    }

    // ---------- DELETE ----------

    @Test
    void eliminaEntidadeSemRegistosComAuditoriaEliminar() throws Exception {
        Entidade entidade = gravar("9412", TipoEntidade.CM, "CM Eliminar");
        mockMvc.perform(patch(URL + "/" + entidade.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(Map.of("nipc", NIPC_VALIDO))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(URL + "/" + entidade.getId()).with(comoMaster()))
                .andExpect(status().isNoContent());

        assertThat(entidadeRepository.existsById(entidade.getId())).isFalse();
        List<Auditoria> eliminar = auditoria(entidade.getId(), "ELIMINAR");
        assertThat(eliminar).hasSize(1);
        assertThat(eliminar.get(0).getEntidadeId()).isNull();
        assertThat(objectMapper.readTree(eliminar.get(0).getDadosAlteracao()).get("nipc").asText())
                .isEqualTo(NIPC_VALIDO);
        mockMvc.perform(delete(URL + "/" + entidade.getId()).with(comoMaster()))
                .andExpect(status().isNotFound());
    }

    @Test
    void entidadeComContactoInativoDa409ENadaMuda() throws Exception {
        Entidade entidade = gravar("9413", TipoEntidade.CM, "CM Contacto");
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor Teste", "autor-" + UUID.randomUUID() + "@exemplo.pt", "INCM"));
        EntidadeContacto contacto = new EntidadeContacto(entidade, "geral@exemplo.pt", autor);
        contacto.setAtivo(false);
        entidadeContactoRepository.saveAndFlush(contacto);

        mockMvc.perform(delete(URL + "/" + entidade.getId()).with(comoMaster()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("A entidade tem registos associados e não pode ser eliminada."));

        assertThat(entidadeRepository.existsById(entidade.getId())).isTrue();
        assertThat(auditoria(entidade.getId(), "ELIMINAR")).isEmpty();
    }

    @Test
    void chaveEstrangeiraRecusadaPelaBaseDa409ESemAuditoriaEliminar() throws Exception {
        Entidade entidade = gravar("9414", TipoEntidade.CM, "CM Notificada");
        Notificacao notificacao = new Notificacao(TipoNotificacao.ESTATICA, "Aviso", "Texto", null);
        notificacao.setEntidade(entidade);
        notificacaoRepository.saveAndFlush(notificacao);

        mockMvc.perform(delete(URL + "/" + entidade.getId()).with(comoMaster()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("A entidade tem registos associados e não pode ser eliminada."));

        assertThat(entidadeRepository.existsById(entidade.getId())).isTrue();
        assertThat(auditoria(entidade.getId(), "ELIMINAR")).isEmpty();
    }

    // ---------- GET ----------

    @Test
    void filtroNomePesquisaNoCodigoENasDescricoes() throws Exception {
        Entidade entidade = entidadeRepository.saveAndFlush(
                new Entidade("5245", TipoEntidade.CM, null, "Município Pesquisa Incm", "CM Pesquisa Saigt"));

        mockMvc.perform(get(URL).with(comoMaster()).param("nome", "524").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + entidade.getId() + ")].codigo").value("5245"));
        mockMvc.perform(get(URL).with(comoMaster()).param("nome", "pesquisa incm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + entidade.getId() + ")]").exists());
        mockMvc.perform(get(URL).with(comoMaster()).param("nome", "PESQUISA SAIGT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + entidade.getId() + ")].nipc").exists());
    }

    @Test
    void filtroTipoEntidadeDevolveSoPcmOrdenadasPelaDescricaoSaigt() throws Exception {
        gravar("9415", TipoEntidade.PCM, "PCM Zeta");
        gravar("9416", TipoEntidade.PCM, "PCM Alfa");
        gravar("9417", TipoEntidade.PCM, "PCM Meio");
        gravar("9418", TipoEntidade.CM, "PCM Nao E Pcm");

        String corpo = mockMvc.perform(get(URL).with(comoMaster()).param("tipoEntidade", "PCM").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<JsonNode> conteudo = StreamSupport.stream(objectMapper.readTree(corpo).get("content").spliterator(), false)
                .toList();
        assertThat(conteudo).allSatisfy(e -> assertThat(e.get("tipoEntidade").asText()).isEqualTo("PCM"));
        assertThat(conteudo).extracting(e -> e.get("descricaoSaigt").asText())
                .containsSubsequence("PCM Alfa", "PCM Meio", "PCM Zeta")
                .doesNotContain("PCM Nao E Pcm");

        mockMvc.perform(get(URL).with(comoMaster()).param("tipoEntidade", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoEntidade"));
    }

    @Test
    void listagemTrazOsCamposDaEntidadeSaigtSemNif() throws Exception {
        Entidade entidade = new Entidade("T-LST-1", TipoEntidade.CM, "111111128", "Camara Descricao Incm",
                "CM Descricao Saigt");
        entidade.setDico(DICO_ATIVO);
        entidadeRepository.saveAndFlush(entidade);

        mockMvc.perform(get(URL).with(comoMaster()).param("nome", "Camara Descricao Incm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nipc").value("111111128"))
                .andExpect(jsonPath("$.content[0].dico").value(DICO_ATIVO))
                .andExpect(jsonPath("$.content[0].codigo").value("T-LST-1"))
                .andExpect(jsonPath("$.content[0].descricaoIncm").value("Camara Descricao Incm"))
                .andExpect(jsonPath("$.content[0].descricaoSaigt").value("CM Descricao Saigt"))
                .andExpect(jsonPath("$.content[0].tipoEntidade").value("CM"))
                .andExpect(jsonPath("$.content[0].nif").doesNotExist());
    }

    // ---------- autorização ----------

    @Test
    void tecnicoVisitanteEIncmRecebem403NasEscritas() throws Exception {
        Entidade entidade = gravar("9419", TipoEntidade.CM, "CM Protegida");
        String criar = json(Map.of("codigo", "9420", "descricaoIncm", "X", "descricaoSaigt", "X", "tipoEntidade", "CM"));

        for (RequestPostProcessor chamador : List.of(comPerfil("TECNICO"), comPerfil("VISITANTE"), comoIncm())) {
            mockMvc.perform(post(URL).with(chamador).contentType(APPLICATION_JSON).content(criar))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(URL + "/" + entidade.getId()).with(chamador).contentType(APPLICATION_JSON)
                            .content(json(Map.of("descricaoSaigt", "Alterada"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(URL + "/" + entidade.getId()).with(chamador))
                    .andExpect(status().isForbidden());
        }

        assertThat(entidadeRepository.existsByCodigo("9420")).isFalse();
        assertThat(entidadeRepository.findById(entidade.getId()).orElseThrow().getDescricaoSaigt())
                .isEqualTo("CM Protegida");
    }

    @Test
    void tecnicoNaoListaEntidades() throws Exception {
        mockMvc.perform(get(URL).with(comPerfil("TECNICO"))).andExpect(status().isForbidden());
    }

    @Test
    void tecnicoContinuaSemListarEntidadesComOFiltroTipoEntidadeEO403VemAntesDoValorInvalido() throws Exception {
        gravar("9421", TipoEntidade.PCM, "PCM Reservada");

        mockMvc.perform(get(URL).with(comPerfil("TECNICO")).param("tipoEntidade", "PCM"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.content").doesNotExist());
        mockMvc.perform(get(URL).with(comPerfil("TECNICO")).param("tipoEntidade", "XPTO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveDevolver401SemJwt() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
