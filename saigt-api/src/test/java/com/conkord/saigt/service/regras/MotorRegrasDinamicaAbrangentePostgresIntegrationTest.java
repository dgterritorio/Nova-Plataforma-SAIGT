package com.conkord.saigt.service.regras;

import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.regras.avaliador.DinamicaBloqueadaLegadoAvaliador;
import com.conkord.saigt.service.regras.avaliador.OcorrenciaEmEstadoAvaliador;
import com.conkord.saigt.service.regras.avaliador.OcorrenciaNaoExisteAvaliador;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O motor de regras e o `/disponivel` leem os Instrumentos que a Dinâmica abrange (`dinamica_instrumento`), num
 * PostgreSQL migrado pelo {@code master.xml}. Nenhuma Dinâmica abrange hoje mais de um Instrumento: o teste acrescenta
 * a segunda linha de `dinamica_instrumento` à mão.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class MotorRegrasDinamicaAbrangentePostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";

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
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private OcorrenciaNaoExisteAvaliador ocorrenciaNaoExiste;

    @Autowired
    private OcorrenciaEmEstadoAvaliador ocorrenciaEmEstado;

    @Autowired
    private DinamicaBloqueadaLegadoAvaliador dinamicaBloqueadaLegado;

    @Test
    void umaSuspensaoEmCursoIniciadaEmADeveContarTambemEmB() throws Exception {
        Instrumento pdmA = novoPdm();
        Instrumento pdmB = novoPdm();
        Instrumento pdmC = novoPdm();
        String semSuspensaoEmCurso = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"SUSPENSAO\",\"estado\":\"EM_CURSO\"}";
        String semDinamicasEmCurso = "{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}";
        String semSuspensao = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"SUSPENSAO\"}";
        UUID medidas = iniciar(pdmA, "MEDIDAS_PREVENTIVAS");
        publicarAsMedidasPreventivas(medidas);
        jdbcTemplate.update("UPDATE dinamica SET estado = 'TERMINADA', data_conclusao = now() WHERE id = ?", medidas);
        UUID suspensao = iniciar(pdmA, "SUSPENSAO");
        abranger(suspensao, pdmB);

        for (Instrumento abrangido : List.of(pdmA, pdmB)) {
            assertThat(ocorrenciaNaoExiste.avaliar(abrangido.getId(), semSuspensaoEmCurso)).isFalse();
            assertThat(ocorrenciaNaoExiste.avaliar(abrangido.getId(), semDinamicasEmCurso)).isFalse();
            assertThat(ocorrenciaNaoExiste.avaliar(abrangido.getId(), semSuspensao)).isFalse();
        }
        assertThat(ocorrenciaNaoExiste.avaliar(pdmC.getId(), semSuspensaoEmCurso)).isTrue();
        assertThat(ocorrenciaNaoExiste.avaliar(pdmC.getId(), semDinamicasEmCurso)).isTrue();
        assertThat(ocorrenciaNaoExiste.avaliar(pdmC.getId(), semSuspensao)).isTrue();
    }

    @Test
    void aPublicacaoDasMedidasPreventivasNumaDinamicaQueAbrangeAEBDeveContarNosDois() throws Exception {
        Instrumento pdmA = novoPdm();
        Instrumento pdmB = novoPdm();
        Instrumento pdmC = novoPdm();
        String publicacaoPublicada = "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"PUBLICACAO_MP\",\"estado\":\"PUBLICADO\"}";
        String semPublicacao = "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"PUBLICACAO_MP\"}";
        String medidasEmCurso = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"MEDIDAS_PREVENTIVAS\",\"estado\":\"EM_CURSO\"}";
        UUID medidas = iniciar(pdmA, "MEDIDAS_PREVENTIVAS");
        abranger(medidas, pdmB);
        publicarAsMedidasPreventivas(medidas);

        for (Instrumento abrangido : List.of(pdmA, pdmB)) {
            assertThat(ocorrenciaEmEstado.avaliar(abrangido.getId(), publicacaoPublicada)).isTrue();
            assertThat(ocorrenciaEmEstado.avaliar(abrangido.getId(), medidasEmCurso)).isTrue();
            assertThat(ocorrenciaNaoExiste.avaliar(abrangido.getId(), semPublicacao)).isFalse();
        }
        assertThat(ocorrenciaEmEstado.avaliar(pdmC.getId(), publicacaoPublicada)).isFalse();
        assertThat(ocorrenciaEmEstado.avaliar(pdmC.getId(), medidasEmCurso)).isFalse();
        assertThat(ocorrenciaNaoExiste.avaliar(pdmC.getId(), semPublicacao)).isTrue();
    }

    @Test
    void umaDinamicaEspelhadaDoLegadoQueAbrangeDoisInstrumentosDeveBloquearOsDois() throws Exception {
        Instrumento pdmA = novoPdm();
        Instrumento pdmB = novoPdm();
        Instrumento pdmC = novoPdm();
        UUID espelho = iniciar(pdmA, "MEDIDAS_PREVENTIVAS");
        jdbcTemplate.update("UPDATE dinamica SET origem_dados = 'ESPELHO_ABERTO' WHERE id = ?", espelho);
        abranger(espelho, pdmB);
        String parametros = "{\"codigo\":\"MEDIDAS_PREVENTIVAS\"}";

        assertThat(dinamicaBloqueadaLegado.avaliar(pdmA.getId(), parametros)).isTrue();
        assertThat(dinamicaBloqueadaLegado.avaliar(pdmB.getId(), parametros)).isTrue();
        assertThat(dinamicaBloqueadaLegado.avaliar(pdmC.getId(), parametros)).isFalse();
        assertThat(dinamicaBloqueadaLegado.avaliar(pdmB.getId(), "{\"codigo\":\"SUSPENSAO\"}")).isFalse();
    }

    @Test
    void oDisponivelDeBDeveDarAUltimaTerminadaQueOAbrangeSemListarAsEmCursoIniciadasEmA() throws Exception {
        Instrumento pdmA = novoPdm();
        Instrumento pdmB = novoPdm();
        UUID terminada = iniciar(pdmA, "MEDIDAS_PREVENTIVAS");
        jdbcTemplate.update("UPDATE dinamica SET estado = 'TERMINADA', data_conclusao = '2026-11-10T10:00:00Z' WHERE id = ?",
                terminada);
        UUID emCurso = iniciar(pdmA, "NORMAS_PROVISORIAS");
        abranger(terminada, pdmB);
        abranger(emCurso, pdmB);

        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + pdmB.getId() + "/disponivel").with(camaraDeSintra()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dinamicaId").value(terminada.toString()))
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.tipoDinamica").value("MEDIDAS_PREVENTIVAS"))
                .andExpect(jsonPath("$.dinamicasEmCurso", empty()));
        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + pdmA.getId() + "/disponivel").with(camaraDeSintra()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dinamicaId").value(terminada.toString()))
                .andExpect(jsonPath("$.dinamicasEmCurso[*].dinamicaId").value(contains(emCurso.toString())));
    }

    @Test
    void criarUmaDinamicaDeveDevolverOsInstrumentosQueAbrangeEGravarALigacao() throws Exception {
        Instrumento pdm = novoPdm();

        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pdm.getId() + "/dinamicas").with(camaraDeSintra())
                        .contentType("application/json").content(pedidoDeDinamica("MEDIDAS_PREVENTIVAS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoId").value(pdm.getId().toString()))
                .andExpect(jsonPath("$.instrumentoIds").value(contains(pdm.getId().toString())))
                .andReturn().getResponse().getContentAsString();
        UUID dinamica = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        assertThat(jdbcTemplate.queryForList("SELECT instrumento_id FROM dinamica_instrumento WHERE dinamica_id = ?",
                UUID.class, dinamica)).containsExactly(pdm.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica d WHERE NOT EXISTS (SELECT 1 FROM "
                + "dinamica_instrumento di WHERE di.dinamica_id = d.id AND di.instrumento_id = d.instrumento_id)",
                Integer.class)).isZero();
    }

    @Test
    void oProcessoDeUmaDinamicaQueAbrangeOutroInstrumentoContinuaInvisivelParaOutraEntidade() throws Exception {
        Instrumento pdmA = novoPdm();
        Instrumento pdmB = novoPdm();
        UUID medidas = iniciar(pdmA, "MEDIDAS_PREVENTIVAS");
        UUID processo = criarPublicacaoDasMedidasPreventivas(medidas);
        abranger(medidas, pdmB);
        String outraCamara = jdbcTemplate.queryForObject("SELECT min(codigo) FROM entidade WHERE tipo_entidade = 'CM' "
                + "AND codigo <> ?", String.class, CODIGO_CAMARA_DE_SINTRA);

        mockMvc.perform(get("/api/v1/processos/" + processo).with(camaraDeSintra()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(sessao(outraCamara)))
                .andExpect(status().isNotFound());
    }

    private Instrumento novoPdm() {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável PDM", "responsavel-" + UUID.randomUUID() + "@teste-abrange.pt", "INCM"));
        String numero = "PDM-AB-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> "PDM".equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID iniciar(Instrumento instrumento, String tipoDinamica) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumento.getId() + "/dinamicas")
                        .with(camaraDeSintra()).contentType("application/json").content(pedidoDeDinamica(tipoDinamica)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private UUID criarPublicacaoDasMedidasPreventivas(UUID medidas) throws Exception {
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + medidas + "/processos").with(camaraDeSintra())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", procedimentoConfigId("MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP"),
                                "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void publicarAsMedidasPreventivas(UUID medidas) throws Exception {
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", criarPublicacaoDasMedidasPreventivas(medidas));
    }

    private void abranger(UUID dinamica, Instrumento instrumento) {
        jdbcTemplate.update("INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) VALUES (?, ?)",
                dinamica, instrumento.getId());
    }

    private String pedidoDeDinamica(String tipoDinamica) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoDinamica),
                "descritivo", tipoDinamica + " de teste"));
    }

    private long dinamicaConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = ?", Long.class, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoDinamica, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ipc.instrumento_dinamica_config_id = ? AND tp.codigo = ?", Long.class,
                dinamicaConfigId(tipoDinamica), tipoProcedimento);
    }

    private RequestPostProcessor camaraDeSintra() {
        return sessao(CODIGO_CAMARA_DE_SINTRA);
    }

    private RequestPostProcessor sessao(String codigoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-abrange.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", "CM", "nif", nipc,
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", entidade));
    }
}
