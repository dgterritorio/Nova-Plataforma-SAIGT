package com.conkord.saigt.db;

import com.conkord.saigt.persistence.operacional.EstadoDinamica;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A configuração semeada das restantes servidões (tipo SERVIDAO) contra o PostgreSQL migrado pelo {@code master.xml}:
 * quem as pode criar, a elegibilidade da Delimitação, da Alteração e da Revogação e o Procedimento sem série do DR,
 * pelos endpoints, com sessões de entidade com o tipo na claim.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedConfiguracaoRestantesServidoesPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String URL_TIPOS_PROCESSO = "/api/v1/criacao/tipos-processo";
    private static final String SERVIDAO = "SERVIDAO";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    /** `incm_act_type_id_serie_2` do Despacho (seed `tipo_ato`), o único ato autorizado nas sessões. */
    private static final long ID_INCM_DESPACHO = 11L;

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

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    @Test
    void aCmASecretariaDeEstadoEAIpCriamServidoesEACimNaoAsVeNemCria() throws Exception {
        Sessao cim = sessao("CIM");
        List<Long> procedimentosDaServidao = procedimentos();

        for (String tipoEntidade : List.of("CM", "SEC_ESTADO", "IP")) {
            Sessao emitente = sessao(tipoEntidade);
            mockMvc.perform(get("/api/v1/tipos-instrumento").with(emitente.jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].codigo").value(hasItem(SERVIDAO)));
            mockMvc.perform(get(URL_TIPOS_PROCESSO).with(emitente.jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo").value(hasItem(SERVIDAO)));
            assertThat(procedimentosPermitidos(emitente)).as(tipoEntidade).containsAll(procedimentosDaServidao);
            criar(emitente);
        }

        // O catálogo GET /tipos-instrumento é igual para todos (contrato); o assistente lista o que a sessão pode iniciar.
        mockMvc.perform(get(URL_TIPOS_PROCESSO).with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo").value(not(hasItem(SERVIDAO))));
        assertThat(procedimentosPermitidos(cim)).doesNotContainAnyElementsOf(procedimentosDaServidao);
        int instrumentosAntes = contar("SELECT count(*) FROM instrumento");
        mockMvc.perform(post(URL_INSTRUMENTOS).with(cim.jwt()).contentType("application/json").content(pedidoInstrumento()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes);
    }

    @Test
    void aCimRecebe403AoPedirOProcedimentoDeUmaServidaoDaCamara() throws Exception {
        Sessao camara = sessao("CM");
        Sessao cim = sessao("CIM");
        UUID servidao = criar(camara);
        UUID delimitacao = iniciar(camara, servidao, "DELIMITACAO");

        mockMvc.perform(post("/api/v1/dinamicas/" + delimitacao + "/processos").with(cim.jwt())
                        .contentType("application/json").content(pedidoProcesso(procedimentoConfigId("DELIMITACAO"), despacho())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + servidao + "/dinamicas").with(cim.jwt())
                        .contentType("application/json").content(pedidoDinamica("ALTERACAO")))
                .andExpect(status().isForbidden());
        assertThat(contar("SELECT count(*) FROM processo WHERE dinamica_id = '" + delimitacao + "'")).isZero();
    }

    @Test
    void umaServidaoNovaSoOfereceADelimitacaoEDepoisDeDelimitadaEmVigorAAlteracaoEARevogacao() throws Exception {
        Sessao camara = sessao("CM");
        UUID servidao = criar(camara);

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("DELIMITACAO")));

        terminar(iniciar(camara, servidao, "DELIMITACAO"));
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", servidao);

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("ALTERACAO", "REVOGACAO")));
        recusarDinamica(camara, servidao, "DELIMITACAO");

        // A Alteração repete-se: depois de uma terminada, volta a ser oferecida.
        terminar(iniciar(camara, servidao, "ALTERACAO"));
        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("ALTERACAO", "REVOGACAO")));
    }

    @Test
    void umaServidaoRevogadaNaoOfereceNenhumaDinamicaERecusaOutraRevogacao() throws Exception {
        Sessao camara = sessao("CM");
        UUID servidao = criar(camara);
        terminar(iniciar(camara, servidao, "DELIMITACAO"));
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", servidao);

        terminar(iniciar(camara, servidao, "REVOGACAO"));
        // A publicação da Revogação tira a servidão de vigor (TR-119).
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = false WHERE id = ?", servidao);

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", empty()));
        for (String tipoDinamica : List.of("REVOGACAO", "ALTERACAO", "DELIMITACAO")) {
            recusarDinamica(camara, servidao, tipoDinamica);
        }
        assertThat(contar("SELECT count(*) FROM dinamica WHERE instrumento_id = '" + servidao + "'")).isEqualTo(2);
    }

    @Test
    void comUmaRevogacaoEmCursoOutraRevogacaoNaoEElegivel() throws Exception {
        Sessao camara = sessao("CM");
        UUID servidao = criar(camara);
        terminar(iniciar(camara, servidao, "DELIMITACAO"));
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", servidao);
        iniciar(camara, servidao, "REVOGACAO");

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("ALTERACAO")));
        recusarDinamica(camara, servidao, "REVOGACAO");
        assertThat(contar("SELECT count(*) FROM dinamica d JOIN instrumento_dinamica_config c "
                + "ON c.id = d.instrumento_dinamica_config_id JOIN tipo_dinamica t ON t.id = c.tipo_dinamica_id "
                + "WHERE d.instrumento_id = '" + servidao + "' AND t.codigo = 'REVOGACAO'")).isEqualTo(1);
    }

    @Test
    void umaServidaoDoSnitEmVigorSemDinamicasNaSaigtOfereceAAlteracaoEARevogacao() throws Exception {
        Sessao camara = sessao("CM");
        UUID servidao = criar(camara);
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true, id_igt_snit = ? WHERE id = ?", "SNIT-SRUP-" + servidao,
                servidao);

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("ALTERACAO", "REVOGACAO")));
    }

    @Test
    void oProcessoDaDelimitacaoCriaSeSemSerieDoDrEComOsTiposDeAtoPorSerie() throws Exception {
        Sessao camara = sessao("CM");
        UUID servidao = criar(camara);
        UUID delimitacao = iniciar(camara, servidao, "DELIMITACAO");

        mockMvc.perform(get(disponivel(servidao)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].tipoProcedimento")
                        .value(contains("PUBLICACAO_SRUP")))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].serieDr").value(nullValue()))
                // Sem série no Procedimento, cada tipo vem com as séries em que existe (escolhe-se no processo).
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tiposAtoDisponiveis[*].seriesDr[*]")
                        .value(contains("SEGUNDA_H")));

        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + delimitacao + "/processos").with(camara.jwt())
                        .contentType("application/json").content(pedidoProcesso(procedimentoConfigId("DELIMITACAO"), despacho())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID processo = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        mockMvc.perform(get("/api/v1/processos/" + processo).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieDr").value(nullValue()));
        assertThat(contar("SELECT count(*) FROM processo WHERE dinamica_id = '" + delimitacao + "'")).isEqualTo(1);
    }

    private Sessao sessao(String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-srup.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", ID_INCM_DESPACHO, "nome", "Despacho", "extrato", false))));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade)), utilizadorId);
    }

    private List<Long> procedimentosPermitidos(Sessao sessao) throws Exception {
        JsonNode permissoes = objectMapper.readTree(mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(sessao.jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(permissoes.get("procedimentosPermitidos").spliterator(), false).map(JsonNode::asLong).toList();
    }

    private List<Long> procedimentos() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = ?", Long.class, SERVIDAO);
        assertThat(ids).hasSize(3);
        return ids;
    }

    private String pedidoInstrumento() throws Exception {
        long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, SERVIDAO);
        return objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoId,
                "designacao", "Servidão de teste " + UUID.randomUUID(), "concelhos", List.of("1111")));
    }

    private UUID criar(Sessao quem) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json")
                        .content(pedidoInstrumento()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private String pedidoDinamica(String tipoDinamica) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoDinamica),
                "descritivo", tipoDinamica + " da servidão"));
    }

    private UUID iniciar(Sessao quem, UUID instrumento, String tipoDinamica) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumento + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(pedidoDinamica(tipoDinamica)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void recusarDinamica(Sessao quem, UUID instrumento, String tipoDinamica) throws Exception {
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumento + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(pedidoDinamica(tipoDinamica)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
    }

    private void terminar(UUID dinamica) {
        jdbcTemplate.update("UPDATE dinamica SET estado = ?, data_conclusao = ? WHERE id = ?", EstadoDinamica.TERMINADA.name(),
                Timestamp.from(Instant.parse("2026-11-13T10:00:00Z")), dinamica);
    }

    private long despacho() {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class,
                ID_INCM_DESPACHO);
    }

    private String pedidoProcesso(long procedimentoConfigId, long tipoAtoId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoProcedimentoConfigId", procedimentoConfigId,
                "tipoAtoId", tipoAtoId));
    }

    private static String disponivel(UUID instrumento) {
        return URL_INSTRUMENTOS + "/" + instrumento + "/disponivel";
    }

    private long dinamicaConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, SERVIDAO, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "WHERE ipc.instrumento_dinamica_config_id = ?", Long.class, dinamicaConfigId(tipoDinamica));
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
