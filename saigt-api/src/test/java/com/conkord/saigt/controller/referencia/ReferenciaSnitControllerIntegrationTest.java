package com.conkord.saigt.controller.referencia;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferenciaSnitControllerIntegrationTest extends PostgresTestcontainersBase {

    private static final String NUT2 = "/api/v1/referencias-snit/NUT2";
    private static final String MUNICIPIOS = "/api/v1/referencias-snit/MUNICIPALITIES";
    private static final String LVT = "LISBOA E VALE DO TEJO";
    private static final Instant AGORA = Instant.parse("2026-11-09T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SnitReferenciaRepository repository;

    /** A réplica volta a ficar vazia: com concelhos, a criação de Instrumentos dos outros testes passa a validar por ela. */
    @AfterEach
    void esvaziarReplica() {
        repository.deleteAll();
    }

    @BeforeEach
    void preencherReplica() {
        repository.deleteAll();
        nutII("NORTE", "Norte", true);
        nutII("CENTRO", "Centro", true);
        nutII(LVT, "Lisboa e Vale do Tejo", true);
        nutII("ALGARVE", "Algarve", true);
        nutII("ACORES", "Açores", true);
        nutII("ALENTEJO", "Alentejo", false);

        concelho("0412", "VINHAIS", "NORTE", true);
        concelho("0499", "CONCELHO EXTINTO", "NORTE", false);
        concelho("0101", "ÁGUEDA", "CENTRO", true);
        concelho("1105", "CASCAIS", LVT, true);
        concelho("1111", "SINTRA", LVT, true);
        concelho("0705", "ÉVORA", "ALENTEJO", true);
        concelho("0801", "ALBUFEIRA", "ALGARVE", false);
        semNutII("9998", "CONCELHO SEM NUTS II");
    }

    private void nutII(String codigo, String nome, boolean ativo) {
        guardar(ListaReferenciaSnit.NUT2, codigo, nome, "{\"code\":\"" + codigo + "\",\"label\":\"" + nome + "\"}", ativo);
    }

    private void concelho(String dtcc, String nome, String nut2, boolean ativo) {
        guardar(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\",\"nut2Code\":\"" + nut2 + "\"}", ativo);
    }

    private void semNutII(String dtcc, String nome) {
        guardar(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\"}", true);
    }

    private void guardar(ListaReferenciaSnit lista, String codigo, String nome, String dadosOrigem, boolean ativo) {
        SnitReferencia referencia = new SnitReferencia(lista, codigo, nome, dadosOrigem, AGORA);
        referencia.setAtivo(ativo);
        repository.save(referencia);
    }

    private static RequestPostProcessor comoInterno() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    private static RequestPostProcessor comoEntidade() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("nif", "999888777", "nome", "Entidade Teste", "pcm", false)));
    }

    @Test
    void nut2DeveDevolverSoAsAtivasComConcelhoAtivoOrdenadasPeloNome() throws Exception {
        mockMvc.perform(get(NUT2).with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("CENTRO", LVT, "NORTE")))
                .andExpect(jsonPath("$[*].nome", contains("Centro", "Lisboa e Vale do Tejo", "Norte")))
                .andExpect(jsonPath("$[0].nut2").doesNotExist());
    }

    @Test
    void nut2DeveIgnorarOFiltroNut2() throws Exception {
        mockMvc.perform(get(NUT2).param("nut2", "NORTE").with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void concelhosDeUmaNutIiDevemSerSoOsDessaNutIiComDtccDeQuatroAlgarismos() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", "NORTE").with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("0412"))
                .andExpect(jsonPath("$[0].nome").value("VINHAIS"))
                .andExpect(jsonPath("$[0].nut2").value("NORTE"));
    }

    @Test
    void concelhosDeLisboaEValeDoTejoDevemTerSintra() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", LVT).with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("1105", "1111")));
    }

    @Test
    void concelhosSemFiltroDevemSerTodosOsAtivosOrdenadosPelaOrdemPortuguesa() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("0101", "1105", "9998", "0705", "1111", "0412")))
                .andExpect(jsonPath("$[2].nut2").doesNotExist());
    }

    @Test
    void concelhosComNut2VazioDevemSerTodos() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", "  ").with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void concelhosDeUmaNutIiDesconhecidaDevemSerUmaListaVazia() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", "MARTE").with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void concelhosComNut2ComoTextoDeSqlNaoDevemDevolverNada() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", "' OR '1'='1").with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void nut2ComMaisDeCinquentaCaracteresDeveReceber400() throws Exception {
        mockMvc.perform(get(MUNICIPIOS).param("nut2", "X".repeat(51)).with(comoInterno()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("nut2"));
    }

    @Test
    void listaForaDeNut2EMunicipalitiesDeveReceber400() throws Exception {
        for (String lista : new String[] {"CLASSES", "INSTRUMENT_TYPES", "nut2", "OUTRA"}) {
            mockMvc.perform(get("/api/v1/referencias-snit/" + lista).with(comoInterno()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Lista não disponível."))
                    .andExpect(jsonPath("$.campos[0].path").value("lista"));
        }
    }

    @Test
    void qualquerUtilizadorAutenticadoDeveConseguirLer() throws Exception {
        mockMvc.perform(get(NUT2).with(comoEntidade())).andExpect(status().isOk());
        mockMvc.perform(get(MUNICIPIOS).with(comoEntidade())).andExpect(status().isOk());
    }

    @Test
    void aRespostaDeveSerGuardadaPorUmaHora() throws Exception {
        mockMvc.perform(get(NUT2).with(comoInterno()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600"));
    }

    @Test
    void semTokenDeveReceber401() throws Exception {
        mockMvc.perform(get(NUT2)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MUNICIPIOS)).andExpect(status().isUnauthorized());
    }
}
