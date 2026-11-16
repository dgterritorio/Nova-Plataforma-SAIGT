package com.conkord.saigt.controller.entidade;

import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntidadeContactoControllerIntegrationTest {

    private static final AtomicLong NIPC = new AtomicLong(603_403_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private EntidadeContactoRepository entidadeContactoRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Utilizador master;
    private Entidade entidade;

    @BeforeEach
    void preparar() {
        Utilizador novo = new Utilizador("Master Contactos", "master.contactos." + UUID.randomUUID() + "@dgt.pt", "AD_DGT");
        novo.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.saveAndFlush(novo);
        entidade = novaEntidade("Câmara Municipal de Sintra");
    }

    private Entidade novaEntidade(String descricao) {
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova(String.valueOf(NIPC.incrementAndGet()), descricao));
    }

    /** `criado_por` exige um `utilizador` real. */
    private RequestPostProcessor comoMaster() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, master.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private RequestPostProcessor comoTecnico() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    private RequestPostProcessor comoIncm() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM").claim("entidade_id", entidade.getId()));
    }

    private EntidadeContacto contacto(Entidade dona, String email, boolean principal, boolean ativo) {
        EntidadeContacto contacto = new EntidadeContacto(dona, email, master);
        contacto.setPrincipal(principal);
        contacto.setAtivo(ativo);
        return entidadeContactoRepository.saveAndFlush(contacto);
    }

    private String url() {
        return "/api/v1/entidades/" + entidade.getId() + "/contactos";
    }

    private String url(EntidadeContacto contacto) {
        return url() + "/" + contacto.getId();
    }

    private List<EntidadeContacto> principaisAtivos() {
        return entidadeContactoRepository.findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(entidade.getId())
                .stream().filter(c -> c.isPrincipal() && c.isAtivo()).toList();
    }

    private List<Auditoria> auditoria(String acao) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "ENTIDADE_CONTACTO", entidade.getId().toString(), acao);
    }

    // --- GET

    @Test
    void listaOsTresComOPrincipalPrimeiroEOInativoNoFim() throws Exception {
        EntidadeContacto inativo = contacto(entidade, "a-inativo@cm-sintra.pt", false, false);
        EntidadeContacto ativo = contacto(entidade, "b-ativo@cm-sintra.pt", false, true);
        EntidadeContacto principal = contacto(entidade, "c-principal@cm-sintra.pt", true, true);

        mockMvc.perform(get(url()).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(principal.getId()))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].criadoEm").value(notNullValue()))
                .andExpect(jsonPath("$[1].id").value(ativo.getId()))
                .andExpect(jsonPath("$[1].principal").value(false))
                .andExpect(jsonPath("$[2].id").value(inativo.getId()))
                .andExpect(jsonPath("$[2].ativo").value(false));
    }

    @Test
    void listarEntidadeInexistenteDa404() throws Exception {
        mockMvc.perform(get("/api/v1/entidades/999999/contactos").with(comoMaster()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Entidade não encontrada."));
    }

    // --- POST

    @Test
    void criaSemPrincipalComPrincipalNaEntidadeComoNaoPrincipalEAtivoEAudita() throws Exception {
        contacto(entidade, "geral@cm-sintra.pt", true, true);

        String corpo = mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"urbanismo@cm-sintra.pt\",\"descricao\":\"Urbanismo\",\"telefone\":\"219238500\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.email").value("urbanismo@cm-sintra.pt"))
                .andExpect(jsonPath("$.descricao").value("Urbanismo"))
                .andExpect(jsonPath("$.telefone").value("219238500"))
                .andExpect(jsonPath("$.principal").value(false))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(corpo).get("id").asLong();

        List<Auditoria> criar = auditoria("CRIAR_CONTACTO");
        assertThat(criar).hasSize(1);
        assertThat(criar.get(0).getUtilizadorId()).isEqualTo(master.getId());
        JsonNode dados = objectMapper.readTree(criar.get(0).getDadosAlteracao());
        assertThat(dados.get("contactoId").asLong()).isEqualTo(id);
        assertThat(dados.get("email").get("antes").isNull()).isTrue();
        assertThat(dados.get("email").get("depois").asText()).isEqualTo("urbanismo@cm-sintra.pt");
        assertThat(dados.get("principal").get("depois").asBoolean()).isFalse();
    }

    @Test
    void criaPrincipalNumaEntidadeSemNenhum() throws Exception {
        mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"geral@cm-sintra.pt\",\"principal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(true))
                .andExpect(jsonPath("$.descricao").value(nullValue()));

        assertThat(principaisAtivos()).extracting(EntidadeContacto::getEmail).containsExactly("geral@cm-sintra.pt");
    }

    @Test
    void criarSegundoPrincipalDa409ENadaGrava() throws Exception {
        contacto(entidade, "geral@cm-sintra.pt", true, true);

        mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"outro@cm-sintra.pt\",\"principal\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_DUPLICADA"))
                .andExpect(jsonPath("$.mensagem").value("Contacto duplicado ou principal já definido."));

        assertThat(entidadeContactoRepository.findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(entidade.getId()))
                .extracting(EntidadeContacto::getEmail).containsExactly("geral@cm-sintra.pt");
        assertThat(auditoria("CRIAR_CONTACTO")).isEmpty();
    }

    @Test
    void criarComEmailJaUsadoNaEntidadeDa409() throws Exception {
        contacto(entidade, "geral@cm-sintra.pt", false, false);

        mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"geral@cm-sintra.pt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Contacto duplicado ou principal já definido."));
    }

    @Test
    void criarComTelefoneOuEmailInvalidoDa400() throws Exception {
        mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"geral@cm-sintra.pt\",\"telefone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("telefone"));
        mockMvc.perform(post(url()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("email"));
    }

    @Test
    void criarParaEntidadeInexistenteDa404() throws Exception {
        mockMvc.perform(post("/api/v1/entidades/999999/contactos").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"geral@cm-sintra.pt\"}"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH

    @Test
    void trocarOPrincipalFazSeEmDoisPedidos() throws Exception {
        EntidadeContacto a = contacto(entidade, "a@cm-sintra.pt", true, true);
        EntidadeContacto b = contacto(entidade, "b@cm-sintra.pt", false, true);

        mockMvc.perform(patch(url(b)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"principal\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Contacto duplicado ou principal já definido."));

        mockMvc.perform(patch(url(a)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"principal\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(false));
        mockMvc.perform(patch(url(b)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"principal\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true))
                .andExpect(jsonPath("$.ativo").value(true));

        assertThat(principaisAtivos()).extracting(EntidadeContacto::getId).containsExactly(b.getId());
        List<Auditoria> editar = auditoria("EDITAR_CONTACTO");
        assertThat(editar).hasSize(2);
        JsonNode marcar = objectMapper.readTree(editar.get(1).getDadosAlteracao());
        assertThat(marcar.get("contactoId").asLong()).isEqualTo(b.getId());
        assertThat(marcar.get("principal").get("antes").asBoolean()).isFalse();
        assertThat(marcar.get("principal").get("depois").asBoolean()).isTrue();
        assertThat(marcar.has("email")).isFalse();
    }

    @Test
    void desativarOPrincipalDeixaAEntidadeSemPrincipal() throws Exception {
        EntidadeContacto principal = contacto(entidade, "geral@cm-sintra.pt", true, true);

        mockMvc.perform(patch(url(principal)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        assertThat(principaisAtivos()).isEmpty();
        assertThat(entidadeContactoRepository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(entidade.getId())).isEmpty();
    }

    @Test
    void reativarUmPrincipalInativoComOutroAtivoDa409() throws Exception {
        EntidadeContacto inativo = contacto(entidade, "antigo@cm-sintra.pt", true, false);
        contacto(entidade, "atual@cm-sintra.pt", true, true);

        mockMvc.perform(patch(url(inativo)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isConflict());

        assertThat(entidadeContactoRepository.findById(inativo.getId())).get()
                .extracting(EntidadeContacto::isAtivo).isEqualTo(false);
    }

    @Test
    void telefoneVazioLimpaEAusenteMantem() throws Exception {
        EntidadeContacto contacto = contacto(entidade, "geral@cm-sintra.pt", false, true);
        contacto.setTelefone("219238500");
        contacto = entidadeContactoRepository.saveAndFlush(contacto);

        mockMvc.perform(patch(url(contacto)).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"descricao\":\"Gabinete da Presidência\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("219238500"));
        mockMvc.perform(patch(url(contacto)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"telefone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(nullValue()))
                .andExpect(jsonPath("$.descricao").value("Gabinete da Presidência"));
    }

    @Test
    void telefoneInvalidoEmailVazioECorpoVazioDao400() throws Exception {
        EntidadeContacto contacto = contacto(entidade, "geral@cm-sintra.pt", false, true);

        mockMvc.perform(patch(url(contacto)).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"telefone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("telefone"));
        mockMvc.perform(patch(url(contacto)).with(comoMaster()).contentType(APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("email"));
        mockMvc.perform(patch(url(contacto)).with(comoMaster()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        assertThat(auditoria("EDITAR_CONTACTO")).isEmpty();
    }

    @Test
    void contactoDeOutraEntidadeNoCaminhoDa404() throws Exception {
        Entidade outra = novaEntidade("Câmara Municipal de Cascais");
        EntidadeContacto deOutra = contacto(outra, "geral@cm-cascais.pt", true, true);

        mockMvc.perform(patch(url() + "/" + deOutra.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"descricao\":\"Urbanismo\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(url() + "/999999").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"descricao\":\"Urbanismo\"}"))
                .andExpect(status().isNotFound());
    }

    // --- autorização

    @Test
    void quemNaoEMasterRecebe403NosTres() throws Exception {
        EntidadeContacto contacto = contacto(entidade, "geral@cm-sintra.pt", true, true);

        for (RequestPostProcessor outro : List.of(comoTecnico(), comoIncm())) {
            mockMvc.perform(get(url()).with(outro))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(url()).with(outro).contentType(APPLICATION_JSON).content("{\"email\":\"x@cm-sintra.pt\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(url(contacto)).with(outro).contentType(APPLICATION_JSON).content("{\"ativo\":false}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(principaisAtivos()).hasSize(1);
    }

    @Test
    void semJwtRecebe401() throws Exception {
        mockMvc.perform(get("/api/v1/entidades/1/contactos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/entidades/1/contactos").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"geral@cm-sintra.pt\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/entidades/1/contactos/1").contentType(APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isUnauthorized());
    }
}
