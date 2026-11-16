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

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A configuração semeada do PRA e do PSA contra o PostgreSQL migrado pelo {@code master.xml}: quem pode criar cada
 * programa, a elegibilidade das Dinâmicas e os tipos de ato do Procedimento de publicação, pelos endpoints, com
 * sessões de entidade com o tipo, a parte do DR e os atos autorizados na claim.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedConfiguracaoSgifrPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    /** `incm_act_type_id_serie_2` do Despacho (seed `tipo_ato`), o único ato de 2.ª série autorizado nas sessões. */
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
    void aCcdrSoPodeCriarOPraACimEAAmSoOPsaEACamaraNenhum() throws Exception {
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
        Sessao cim = sessao(CODIGO_CAMARA_DE_SINTRA, "CIM", "H");
        Sessao am = sessao(CODIGO_CAMARA_DE_SINTRA, "AM", "H");
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");

        mockMvc.perform(get("/api/v1/tipos-instrumento").with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(hasItems("PRA", "PSA")));

        assertThat(procedimentosPermitidos(ccdr)).containsAll(procedimentos("PRA")).doesNotContainAnyElementsOf(procedimentos("PSA"));
        assertThat(procedimentosPermitidos(cim)).containsAll(procedimentos("PSA")).doesNotContainAnyElementsOf(procedimentos("PRA"));
        assertThat(procedimentosPermitidos(am)).containsAll(procedimentos("PSA")).doesNotContainAnyElementsOf(procedimentos("PRA"));
        assertThat(procedimentosPermitidos(camara)).doesNotContainAnyElementsOf(procedimentos("PRA"))
                .doesNotContainAnyElementsOf(procedimentos("PSA"));

        int instrumentosAntes = contar("SELECT count(*) FROM instrumento");
        recusarCriacao(ccdr, "PSA");
        recusarCriacao(cim, "PRA");
        recusarCriacao(am, "PRA");
        recusarCriacao(camara, "PRA");
        recusarCriacao(camara, "PSA");
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes);

        criar(ccdr, "PRA");
        criar(cim, "PSA");
        criar(am, "PSA");
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes + 3);
    }

    @Test
    void umPraNovoSoOfereceADelimitacaoEComUmaDinamicaEmCursoRecusaIniciarOutra() throws Exception {
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
        UUID pra = criar(ccdr, "PRA");

        mockMvc.perform(get(disponivel(pra)).with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("DELIMITACAO")));

        iniciar(ccdr, pra, "PRA", "DELIMITACAO");
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", pra);

        mockMvc.perform(get(disponivel(pra)).with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", empty()));
        for (String tipoDinamica : List.of("ALTERACAO", "DELIMITACAO")) {
            mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pra + "/dinamicas").with(ccdr.jwt()).contentType("application/json")
                            .content(pedidoDinamica("PRA", tipoDinamica)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
        }
        assertThat(contar("SELECT count(*) FROM dinamica WHERE instrumento_id = '" + pra + "'")).isEqualTo(1);
    }

    @Test
    void umPsaDelimitadoEmVigorSoOfereceAAlteracaoQueSeRepeteDepoisDeTerminada() throws Exception {
        Sessao cim = sessao(CODIGO_CAMARA_DE_SINTRA, "CIM", "H");
        UUID psa = criar(cim, "PSA");

        terminar(iniciar(cim, psa, "PSA", "DELIMITACAO"));
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", psa);

        mockMvc.perform(get(disponivel(psa)).with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("ALTERACAO")));

        terminar(iniciar(cim, psa, "PSA", "ALTERACAO"));

        mockMvc.perform(get(disponivel(psa)).with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("ALTERACAO")));
        iniciar(cim, psa, "PSA", "ALTERACAO");
        assertThat(contar("SELECT count(*) FROM dinamica WHERE instrumento_id = '" + psa + "'")).isEqualTo(3);
    }

    @Test
    void umPsaDoSnitEmVigorSemDinamicasNaSaigtSoOfereceAAlteracao() throws Exception {
        Sessao am = sessao(CODIGO_CAMARA_DE_SINTRA, "AM", "H");
        UUID psa = criar(am, "PSA");
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true, id_igt_snit = ? WHERE id = ?", "SNIT-PSA-" + psa, psa);

        mockMvc.perform(get(disponivel(psa)).with(am.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("ALTERACAO")));
    }

    @Test
    void aPublicacaoDoPraOfereceSoOsAtosDeSegundaSerieAutorizadosEExigeAParteC() throws Exception {
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
        Sessao ccdrComParteH = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "H");
        UUID pra = criar(ccdr, "PRA");
        UUID dinamica = iniciar(ccdr, pra, "PRA", "DELIMITACAO");
        long publicacao = procedimentoConfigId("PRA", "DELIMITACAO");
        long despacho = jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class,
                ID_INCM_DESPACHO);
        long outroAtoDeSegundaSerie = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato WHERE ativo "
                + "AND incm_act_type_id_serie_2 IS NOT NULL AND incm_act_type_id_serie_2 <> ?", Long.class, ID_INCM_DESPACHO);

        mockMvc.perform(get(disponivel(pra)).with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].tipoProcedimento")
                        .value(contains("PUBLICACAO_SGIFR")))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].serieDr").value("SEGUNDA_C"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tiposAtoDisponiveis[*].id")
                        .value(contains((int) despacho)));
        mockMvc.perform(get(disponivel(pra)).with(ccdrComParteH.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", empty()));

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(ccdrComParteH.jwt())
                        .contentType("application/json").content(pedidoProcesso(publicacao, despacho)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PARTE_DR_NAO_AUTORIZADA"));
        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(ccdr.jwt())
                        .contentType("application/json").content(pedidoProcesso(publicacao, outroAtoDeSegundaSerie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TIPO_ATO_NAO_AUTORIZADO"));
        assertThat(contar("SELECT count(*) FROM processo WHERE dinamica_id = '" + dinamica + "'")).isZero();

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(ccdr.jwt())
                        .contentType("application/json").content(pedidoProcesso(publicacao, despacho)))
                .andExpect(status().isCreated());
        assertThat(contar("SELECT count(*) FROM processo WHERE dinamica_id = '" + dinamica + "'")).isEqualTo(1);
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-sgifr.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte),
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

    private List<Long> procedimentos(String tipoInstrumento) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = ?", Long.class, tipoInstrumento);
        assertThat(ids).hasSize(2);
        return ids;
    }

    private String pedidoInstrumento(String tipo) throws Exception {
        long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, tipo);
        return objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoId,
                "designacao", tipo + " de teste " + UUID.randomUUID(), "concelhos", List.of("1111")));
    }

    private UUID criar(Sessao quem, String tipo) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json")
                        .content(pedidoInstrumento(tipo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void recusarCriacao(Sessao quem, String tipo) throws Exception {
        mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json").content(pedidoInstrumento(tipo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    private String pedidoDinamica(String tipoInstrumento, String tipoDinamica) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoInstrumento, tipoDinamica),
                "descritivo", tipoDinamica + " do " + tipoInstrumento));
    }

    private UUID iniciar(Sessao quem, UUID instrumento, String tipoInstrumento, String tipoDinamica) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumento + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(pedidoDinamica(tipoInstrumento, tipoDinamica)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void terminar(UUID dinamica) {
        jdbcTemplate.update("UPDATE dinamica SET estado = ?, data_conclusao = ? WHERE id = ?", EstadoDinamica.TERMINADA.name(),
                Timestamp.from(Instant.parse("2026-11-12T10:00:00Z")), dinamica);
    }

    private String pedidoProcesso(long procedimentoConfigId, long tipoAtoId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoProcedimentoConfigId", procedimentoConfigId,
                "tipoAtoId", tipoAtoId));
    }

    private static String disponivel(UUID instrumento) {
        return URL_INSTRUMENTOS + "/" + instrumento + "/disponivel";
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "WHERE ipc.instrumento_dinamica_config_id = ?", Long.class, dinamicaConfigId(tipoInstrumento, tipoDinamica));
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
