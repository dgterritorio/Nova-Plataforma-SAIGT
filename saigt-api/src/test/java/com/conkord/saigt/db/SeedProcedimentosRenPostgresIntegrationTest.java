package com.conkord.saigt.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os Procedimentos da REN semeados contra o PostgreSQL migrado pelo {@code master.xml}: a CCDR cria a REN e só
 * começa pela Delimitação, a Câmara não a vê, e o Procedimento de publicação tem o formulário `REN` na 2.ª série C.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedProcedimentosRenPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15-alpine")
            .withEnv("POSTGRES_PASSWORD", "teste")
            .withEnv("POSTGRES_DB", "saigt")
            .withCopyToContainer(Transferable.of("CREATE ROLE saigt_app LOGIN PASSWORD 'teste';"),
                    "/docker-entrypoint-initdb.d/01-saigt-app.sql")
            .withExposedPorts(PORTA_POSTGRES)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PORTA_POSTGRES) + "/saigt";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "saigt_app");
        registry.add("spring.datasource.password", () -> "teste");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.url", () -> url);
        registry.add("spring.liquibase.user", () -> "postgres");
        registry.add("spring.liquibase.password", () -> "teste");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aCcdrVeARenEUmaRenNovaSoOfereceADelimitacaoComOFormularioRen() throws Exception {
        RequestPostProcessor ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");

        mockMvc.perform(get("/api/v1/tipos-instrumento").with(ccdr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(hasItem("REN")));
        assertThat(procedimentosPermitidos(ccdr)).containsAll(procedimentosDaRen());

        UUID ren = criarRen(ccdr);
        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + ren + "/disponivel").with(ccdr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("DELIMITACAO")));
    }

    @Test
    void aCamaraNaoPodeAgirNosProcedimentosDaRen() throws Exception {
        RequestPostProcessor camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");

        assertThat(procedimentosPermitidos(camara)).doesNotContainAnyElementsOf(procedimentosDaRen());
    }

    private RequestPostProcessor sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-ren.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte),
                "atos_autorizados", Map.of("serie_1", List.of(), "serie_2", List.of()));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM").claim("entidade", entidade));
    }

    private List<Long> procedimentosPermitidos(RequestPostProcessor sessao) throws Exception {
        JsonNode permissoes = objectMapper.readTree(mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(sessao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(permissoes.get("procedimentosPermitidos").spliterator(), false).map(JsonNode::asLong).toList();
    }

    private List<Long> procedimentosDaRen() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id AND tp.codigo = 'PUBLICACAO_REN' "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = 'REN'", Long.class);
        assertThat(ids).hasSize(3);
        return ids;
    }

    private UUID criarRen(RequestPostProcessor quem) throws Exception {
        long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = 'REN'", Long.class);
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoId,
                "designacao", "REN de teste " + UUID.randomUUID(), "concelhos", List.of("1111")));
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(quem).contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }
}
