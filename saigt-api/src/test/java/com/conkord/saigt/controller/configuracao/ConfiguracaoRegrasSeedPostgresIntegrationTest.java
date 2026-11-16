package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.regras.AvaliadorRegistoValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As regras gravadas pela API contra o PostgreSQL migrado pelo {@code master.xml}: o efeito imediato no `/disponivel`,
 * sem reiniciar, sobre as regras semeadas do PDM e do PROT, e a validação de arranque depois das escritas.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ConfiguracaoRegrasSeedPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String REGRAS = "/api/v1/configuracao/regras";
    private static final String INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String SINTRA = "1111";

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
    @Autowired
    private TransactionTemplate transacao;
    @Autowired
    private AvaliadorRegistoValidator avaliadorRegistoValidator;
    @Autowired
    private InstrumentoRepository instrumentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired
    private DinamicaRepository dinamicaRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void umaRegraNovaDeRevisaoUnicaEmCursoNoProtTiraARevisaoDoDisponivelSemReiniciar() throws Exception {
        long protRevisao = dinamicaConfigId("PROT", "REVISAO");
        // A configuração semeada já tem a sua regra: desativada, o PROT fica sem regras ativas, como pede o cenário.
        alterarAtivo(regraSemeada(protRevisao), false);

        Sessao dgt = sessao(CODIGO_CCDR_LVT, "DGT");
        UUID prot = criarInstrumento(dgt, "PROT");
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", prot);
        iniciarDinamica(prot, protRevisao, dgt);

        mockMvc.perform(get(disponivel(prot)).with(dgt.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(hasItem("REVISAO")));

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("instrumentoDinamicaConfigId", protRevisao, "operador", "AND", "substituiSequenciamento", false,
                                "ativo", true, "condicoes", List.of(Map.of("avaliador", "OCORRENCIA_NAO_EXISTE",
                                        "parametros", Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "REVISAO",
                                                "estado", "EM_CURSO"), "grupo", 1, "ativo", true))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoDinamicaConfigId").value(protRevisao))
                .andExpect(jsonPath("$.condicoes[0].avaliador").value("OCORRENCIA_NAO_EXISTE"))
                .andExpect(jsonPath("$.condicoes[0].parametros.estado").value("EM_CURSO"));

        mockMvc.perform(get(disponivel(prot)).with(dgt.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("REVISAO"))))
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(hasItem("ALTERACAO")));

        assertThatCode(() -> avaliadorRegistoValidator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void desativarARegraDeElaboracaoUnicaDoPdmVoltaAOferecerAElaboracaoEReativarTiraADeNovo() throws Exception {
        long pdmElaboracao = dinamicaConfigId("PDM", "ELABORACAO");
        long regra = regraSemeada(pdmElaboracao);
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM");
        UUID pdm = criarInstrumento(camara, "PDM");
        iniciarDinamica(pdm, pdmElaboracao, camara);

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("ELABORACAO"))));

        alterarAtivo(regra, false);

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(hasItem("ELABORACAO")));
        assertThatCode(() -> avaliadorRegistoValidator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        alterarAtivo(regra, true);

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("ELABORACAO"))));
    }

    @Test
    void asRegrasSemeadasPassamNaValidacaoDaGravacaoTalComoEstao() throws Exception {
        for (Long regra : jdbcTemplate.queryForList("SELECT id FROM regra ORDER BY id", Long.class)) {
            JsonNode atual = obter(regra);
            mockMvc.perform(put(REGRAS + "/" + regra).with(comoMaster()).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(semId(atual))))
                    .andExpect(status().isOk());
        }
        assertThatCode(() -> avaliadorRegistoValidator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /** Reenvia a regra como está, só com outro `ativo`. */
    private void alterarAtivo(long regra, boolean ativo) throws Exception {
        ObjectNode corpo = semId(obter(regra));
        corpo.put("ativo", ativo);
        mockMvc.perform(put(REGRAS + "/" + regra).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(ativo));
    }

    private JsonNode obter(long regra) throws Exception {
        String pagina = mockMvc.perform(get(REGRAS).param("size", "100").param("instrumentoDinamicaConfigId",
                        String.valueOf(jdbcTemplate.queryForObject("SELECT coalesce(instrumento_dinamica_config_id, -1) "
                                + "FROM regra WHERE id = ?", Long.class, regra))).with(comoMaster()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode conteudo = objectMapper.readTree(pagina).get("content");
        if (conteudo.isEmpty()) {
            pagina = mockMvc.perform(get(REGRAS).param("instrumentoProcedimentoConfigId", String.valueOf(
                            jdbcTemplate.queryForObject("SELECT instrumento_procedimento_config_id FROM regra WHERE id = ?",
                                    Long.class, regra))).with(comoMaster()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            conteudo = objectMapper.readTree(pagina).get("content");
        }
        return StreamSupport.stream(conteudo.spliterator(), false)
                .filter(no -> no.get("id").asLong() == regra).findFirst().orElseThrow();
    }

    private static ObjectNode semId(JsonNode regra) {
        ObjectNode corpo = regra.deepCopy();
        corpo.remove("id");
        return corpo;
    }

    private long regraSemeada(long dinamicaConfigId) {
        return jdbcTemplate.queryForObject("SELECT min(id) FROM regra WHERE instrumento_dinamica_config_id = ?",
                Long.class, dinamicaConfigId);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT idc.id FROM instrumento_dinamica_config idc "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo = ? AND td.codigo = ?",
                Long.class, tipoInstrumento, tipoDinamica);
    }

    private void iniciarDinamica(UUID instrumentoId, long dinamicaConfigId, Sessao quem) {
        transacao.executeWithoutResult(estado -> dinamicaRepository.saveAndFlush(new Dinamica(
                instrumentoRepository.findById(instrumentoId).orElseThrow(),
                dinamicaConfigRepository.findById(dinamicaConfigId).orElseThrow(), "Dinâmica de teste",
                utilizadorRepository.findById(quem.utilizadorId()).orElseThrow())));
    }

    private static String disponivel(UUID instrumentoId) {
        return INSTRUMENTOS + "/" + instrumentoId + "/disponivel";
    }

    private UUID criarInstrumento(Sessao quem, String tipo) throws Exception {
        Long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, tipo);
        String resposta = mockMvc.perform(post(INSTRUMENTOS).with(quem.jwt()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoId,
                                "designacao", tipo + " de teste " + UUID.randomUUID(), "concelhos", List.of(SINTRA)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    /** A `auditoria` tem FK para `utilizador` no schema do Liquibase: o MASTER tem de existir. */
    private RequestPostProcessor comoMaster() {
        UUID masterId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao, perfil_interno) "
                + "VALUES (?, ?, 'AD_DGT', 'MASTER')",
                masterId, "master-" + masterId + "@teste-regras.pt");
        return jwt().jwt(builder -> builder.claim(SUB, masterId.toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-regras.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade)), utilizadorId);
    }
}
