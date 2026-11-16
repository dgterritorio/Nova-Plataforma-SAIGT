package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.DinamicaService;
import com.conkord.saigt.service.regras.AvaliadorRegistoValidator;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O seed dos 5 atos pontuais de «Outros atos e anexos» contra o PostgreSQL migrado pelo {@code master.xml}: as
 * Dinâmicas abrangidas, as colunas, as permissões copiadas da Decisão de início e o fim de uma Dinâmica sem eles.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedAtosPontuaisPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";

    /** Elaboração, Revisão, Alteração e as duas Alterações Simplificadas de PDM, PP e PU (a RERAE, n.º 4, saiu: OI-545). */
    private static final List<Integer> DINAMICAS_DOS_PLANOS_MUNICIPAIS = Stream.of(100, 2100, 3100)
            .flatMap(base -> IntStream.rangeClosed(1, 6).filter(n -> n != 4).mapToObj(n -> base + n)).toList();

    /** Alteração por Adaptação, Correção Material e Revogação de PDM, PP e PU. */
    private static final List<Integer> DINAMICAS_SEM_DECISAO_NEM_DISCUSSAO = Stream.of(100, 2100, 3100)
            .flatMap(base -> IntStream.rangeClosed(7, 9).mapToObj(n -> base + n)).toList();

    private static final String CODIGOS_DECISAO_E_DISCUSSAO = "'DECISAO_INICIO', 'ABERTURA_DISCUSSAO_PUBLICA', "
            + "'ABERTURA_DISCUSSAO_PUBLICA_DL10'";

    /** Dois Procedimentos obrigatórios, Discussão pública e Alteração (era a RERAE, que saiu com o OI-545). */
    private static final String DINAMICA_DE_DOIS_PROCEDIMENTOS = "ALTERACAO_SIMPLIFICADA_DL10_2024";

    /** O Procedimento de onde se copiam a série e as permissões: a Decisão de início ou, sem ela, o primeiro por ordem. */
    private static final String ORIGEM = "JOIN LATERAL (SELECT o.id, o.serie_dr FROM instrumento_procedimento_config o "
            + "JOIN tipo_procedimento ot ON ot.id = o.tipo_procedimento_id "
            + "WHERE o.instrumento_dinamica_config_id = a.instrumento_dinamica_config_id AND o.ativo AND NOT o.ato_pontual "
            + "ORDER BY ot.codigo = 'DECISAO_INICIO' DESC, o.ordem, o.id LIMIT 1) origem ON true";

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
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private DinamicaService dinamicaService;

    @Test
    void asQuinzeDinamicasDosPlanosMunicipaisDevemTerOsCincoAtosPontuaisComAsColunasDoSeed() {
        for (int dinamica : DINAMICAS_DOS_PLANOS_MUNICIPAIS) {
            assertThat(linhas("SELECT ipc.ordem || ':' || tp.codigo || ':' || ipc.obrigatorio || ':' || ipc.serie_dr || ':' "
                    + "|| ipc.ativo || ':' || ipc.repetivel || ':' || ipc.tamanho_maximo_processo "
                    + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE ipc.instrumento_dinamica_config_id = ? AND ipc.ato_pontual ORDER BY ipc.ordem", dinamica))
                    .as("Dinâmica %d", dinamica).containsExactly(
                            "101:ALTERACAO_ATO_ANEXOS:false:SEGUNDA_H:true:false:5368709120",
                            "102:RETIFICACAO_ATO_ANEXOS:false:SEGUNDA_H:true:false:5368709120",
                            "103:REVOGACAO_ATO:false:SEGUNDA_H:true:false:5368709120",
                            "104:PRORROGACAO_ATO:false:SEGUNDA_H:true:false:5368709120",
                            "105:DECLARACAO_NULIDADE_JUDICIAL:false:SEGUNDA_H:true:false:5368709120");
        }
        assertThat(linhas("SELECT d.id || ':' || count(*) FROM instrumento_procedimento_config p "
                + "JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id "
                + "WHERE p.ato_pontual AND d.tipo_instrumento_id IN (1, 2, 3) GROUP BY d.id ORDER BY d.id"))
                .containsExactlyElementsOf(DINAMICAS_DOS_PLANOS_MUNICIPAIS.stream().map(id -> id + ":5").toList());
    }

    @Test
    void soAsDinamicasComDecisaoDeInicioOuDiscussaoPublicaGanhamAtosPontuais() {
        for (int dinamica : DINAMICAS_SEM_DECISAO_NEM_DISCUSSAO) {
            assertThat(contar("SELECT count(*) FROM instrumento_procedimento_config WHERE ato_pontual "
                    + "AND instrumento_dinamica_config_id = " + dinamica)).as("Dinâmica %d", dinamica).isZero();
        }
        String comDecisaoOuDiscussao = "SELECT idc.id FROM instrumento_dinamica_config idc WHERE idc.ativo AND EXISTS "
                + "(SELECT 1 FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ipc.instrumento_dinamica_config_id = idc.id AND ipc.ativo AND tp.codigo IN (" + CODIGOS_DECISAO_E_DISCUSSAO + "))";
        String comAtosPontuais = "SELECT instrumento_dinamica_config_id FROM instrumento_procedimento_config WHERE ato_pontual";
        assertThat(contar("SELECT count(*) FROM (" + comDecisaoOuDiscussao + " EXCEPT " + comAtosPontuais + ") x")).isZero();
        assertThat(contar("SELECT count(*) FROM (" + comAtosPontuais + " EXCEPT " + comDecisaoOuDiscussao + ") x")).isZero();
        assertThat(linhas("SELECT DISTINCT count(*) || '' FROM instrumento_procedimento_config WHERE ato_pontual "
                + "GROUP BY instrumento_dinamica_config_id")).containsExactly("5");
        assertThat(contar("SELECT count(DISTINCT instrumento_dinamica_config_id) FROM instrumento_procedimento_config "
                + "WHERE ato_pontual")).isEqualTo(41);
    }

    @Test
    void osAtosPontuaisDosPlanosMunicipaisTemACamaraEADgtComoADecisaoDeInicio() {
        String dosPlanosMunicipais = "FROM instrumento_procedimento_config ipc JOIN permissao_acao pa "
                + "ON pa.instrumento_procedimento_config_id = ipc.id WHERE ipc.ato_pontual AND ipc.instrumento_dinamica_config_id IN ("
                + String.join(", ", DINAMICAS_DOS_PLANOS_MUNICIPAIS.stream().map(String::valueOf).toList()) + ")";
        assertThat(linhas("SELECT string_agg(pa.tipo_entidade || '/' || pa.ativo, ',' ORDER BY pa.tipo_entidade) "
                + dosPlanosMunicipais + " GROUP BY ipc.id"))
                .hasSize(75)
                .allSatisfy(linha -> assertThat(linha).isEqualTo("CM/true,DGT/true"));
        assertThat(linhas("SELECT pa.tipo_entidade || ' ' || count(*) " + dosPlanosMunicipais + " GROUP BY pa.tipo_entidade ORDER BY pa.tipo_entidade"))
                .containsExactly("CM 75", "DGT 75");
    }

    @Test
    void cadaAtoPontualCopiaASerieEAsPermissoesAtivasDaDecisaoDeInicioOuDoPrimeiroProcedimento() {
        assertThat(contar("SELECT count(*) FROM instrumento_procedimento_config a " + ORIGEM + " WHERE a.ato_pontual "
                + "AND (a.serie_dr <> origem.serie_dr OR (SELECT string_agg(tipo_entidade, ',' ORDER BY tipo_entidade) "
                + "FROM permissao_acao WHERE instrumento_procedimento_config_id = a.id AND ativo) IS DISTINCT FROM "
                + "(SELECT string_agg(tipo_entidade, ',' ORDER BY tipo_entidade) FROM permissao_acao "
                + "WHERE instrumento_procedimento_config_id = origem.id AND ativo))")).isZero();
        assertThat(linhas("SELECT DISTINCT ti.codigo || ':' || ipc.serie_dr FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ipc.ato_pontual"))
                .containsExactlyInAnyOrder("PDM:SEGUNDA_H", "PP:SEGUNDA_H", "PU:SEGUNDA_H", "PDIM:SEGUNDA_H",
                        "PUIM:SEGUNDA_H", "PPIM:SEGUNDA_H", "PIMOT:SEGUNDA_H", "PNPOT:PRIMEIRA", "PS:PRIMEIRA",
                        "PROT:PRIMEIRA", "PEOT:PRIMEIRA");
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id WHERE ipc.ato_pontual AND pa.tipo_entidade <> 'PCM'"))
                .isEqualTo(500);
    }

    @Test
    void oValidadorDosAvaliadoresDeveArrancarSemErrosSobreABaseMigrada() {
        assertThatCode(() -> avaliadorRegistoValidator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void umaDinamicaSemAtosPontuaisTerminaQuandoSePublicaOUltimoProcedimentoObrigatorio() throws Exception {
        RequestPostProcessor camara = camaraDeSintra();
        Instrumento pdm = novoPdmEmVigor();
        UUID dinamica = iniciar(pdm, DINAMICA_DE_DOIS_PROCEDIMENTOS, camara);

        UUID discussao = criarProcesso(dinamica, "ABERTURA_DISCUSSAO_PUBLICA_DL10", camara);
        publicar(discussao, dinamica);
        assertThat(dinamicaRepository.findById(dinamica).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);

        UUID alteracao = criarProcesso(dinamica, "ALTERACAO_SIMPLIFICADA_DL10", camara);
        publicar(alteracao, dinamica);

        assertThat(dinamicaRepository.findById(dinamica).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(contar("SELECT count(*) FROM processo p JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = p.instrumento_procedimento_config_id WHERE ipc.ato_pontual AND p.dinamica_id = '" + dinamica + "'"))
                .isZero();
    }

    private RequestPostProcessor camaraDeSintra() {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-atos.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", "CM", "nif", nipc,
                "nome", "Entidade de teste " + CODIGO_CAMARA_DE_SINTRA, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
    }

    private Instrumento novoPdmEmVigor() {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável atos", "responsavel-" + UUID.randomUUID() + "@teste-atos.pt", "INCM"));
        String numero = "PDM-ATOS-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> "PDM".equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID iniciar(Instrumento instrumento, String tipoDinamica, RequestPostProcessor quem) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId",
                dinamicaConfigId(tipoDinamica), "descritivo", tipoDinamica + " de teste"));
        String resposta = mockMvc.perform(post("/api/v1/instrumentos/" + instrumento.getId() + "/dinamicas").with(quem)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private UUID criarProcesso(UUID dinamica, String tipoProcedimento, RequestPostProcessor quem) throws Exception {
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        long procedimento = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.instrumento_dinamica_config_id = ? "
                + "AND tp.codigo = ?", Long.class, dinamicaConfigId(DINAMICA_DE_DOIS_PROCEDIMENTOS), tipoProcedimento);
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(quem)
                        .contentType("application/json").content(objectMapper.writeValueAsString(
                                Map.of("instrumentoProcedimentoConfigId", procedimento, "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void publicar(UUID processo, UUID dinamica) {
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", processo);
        transacao.executeWithoutResult(estado ->
                dinamicaService.verificarConclusaoPeloSistema(dinamicaRepository.findById(dinamica).orElseThrow()));
    }

    private long dinamicaConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = ?", Long.class, tipoDinamica);
    }

    private List<String> linhas(String consulta, Object... argumentos) {
        return jdbcTemplate.queryForList(consulta, String.class, argumentos);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
