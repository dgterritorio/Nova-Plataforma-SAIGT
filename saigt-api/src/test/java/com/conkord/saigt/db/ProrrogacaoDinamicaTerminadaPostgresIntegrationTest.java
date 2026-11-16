package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.DinamicaService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.sql.Timestamp;
import java.util.HashMap;
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
 * A Prorrogação numa Dinâmica de Medidas Preventivas já publicada (`TERMINADA`), contra o PostgreSQL migrado pelo
 * {@code master.xml} (a configuração e as permissões vêm do seed): o `/disponivel` e a criação com a mesma regra, a
 * Dinâmica que continua terminada e a Alteração de PDM terminada, que não oferece nada.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ProrrogacaoDinamicaTerminadaPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String ESTADO_INVALIDO = "ESTADO_INVALIDO";

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
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private DinamicaService dinamicaService;

    /** Uma Dinâmica com o Procedimento final publicado e terminada pela consulta à INCM. */
    private record Terminada(Instrumento pdm, UUID dinamica, String tipoDinamica, UUID publicacao, RequestPostProcessor camara) {
    }

    @Test
    void oDisponivelTrazAsMedidasPreventivasTerminadasComSoAProrrogacao() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        String dinamica = dinamicaNoDisponivel(mp);

        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(mp.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamica + ".estado", contains("TERMINADA")))
                .andExpect(jsonPath(dinamica + ".procedimentosDisponiveis[*].tipoProcedimento", contains("PRORROGACAO_MP")))
                .andExpect(jsonPath(dinamica + ".procedimentosDisponiveis[*].obrigatorio", contains(false)))
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dinamicaId").value(mp.dinamica().toString()));
    }

    @Test
    void aCamaraCriaAProrrogacaoEADinamicaContinuaTerminadaComAMesmaDataDeConclusao() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        Timestamp conclusao = dataConclusao(mp);

        UUID prorrogacao = id(criar(mp, "PRORROGACAO_MP").andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO")));

        assertThat(estadoDa(mp)).isEqualTo("TERMINADA");
        assertThat(dataConclusao(mp)).isEqualTo(conclusao);
        // Em `EM_SUBMISSAO` retoma-se: o `/disponivel` continua a oferecê-la, com a tentativa em curso, e a criação recusa outra.
        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(mp.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamicaNoDisponivel(mp) + ".procedimentosDisponiveis[*].tentativaEmCurso.processoId",
                        contains(prorrogacao.toString())));
        criar(mp, "PRORROGACAO_MP").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    @Test
    void aProrrogacaoPublicadaPelaIncmNaoMudaADinamicaNemOEmVigorEDeixaDeSerOferecida() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        Timestamp conclusao = dataConclusao(mp);
        Boolean emVigor = emVigor(mp);
        UUID prorrogacao = id(criar(mp, "PRORROGACAO_MP").andExpect(status().isCreated()));

        publicarPelaIncm(mp, prorrogacao);

        assertThat(estadoDa(mp)).isEqualTo("TERMINADA");
        assertThat(dataConclusao(mp)).isEqualTo(conclusao);
        assertThat(emVigor(mp)).isEqualTo(emVigor);
        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(mp.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamicaNoDisponivel(mp), empty()));
        criar(mp, "PRORROGACAO_MP").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(ESTADO_INVALIDO))
                .andExpect(jsonPath("$.mensagem").value("Esta dinâmica está terminada."));
    }

    @Test
    void comAProrrogacaoEnviadaADgtNadaSeOfereceEAnuladaVoltaASerOferecida() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        UUID prorrogacao = id(criar(mp, "PRORROGACAO_MP").andExpect(status().isCreated()));
        jdbcTemplate.update("UPDATE processo SET estado = 'ENVIADO_DGT' WHERE id = ?", prorrogacao);

        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(mp.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamicaNoDisponivel(mp), empty()));
        criar(mp, "PRORROGACAO_MP").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value(ESTADO_INVALIDO));

        jdbcTemplate.update("UPDATE processo SET estado = 'ANULADO' WHERE id = ?", prorrogacao);
        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(mp.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamicaNoDisponivel(mp) + ".procedimentosDisponiveis[*].tipoProcedimento", contains("PRORROGACAO_MP")));
        criar(mp, "PRORROGACAO_MP").andExpect(status().isCreated()).andExpect(jsonPath("$.ocorrencia").value(2));
    }

    @Test
    void aPublicacaoOutraVezNumaDinamicaTerminadaRecusaCom409EstadoInvalidoSemCriarNada() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        int antes = processosDa(mp);

        criar(mp, "PUBLICACAO_MP").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(ESTADO_INVALIDO))
                .andExpect(jsonPath("$.mensagem").value("Esta dinâmica está terminada."))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(processosDa(mp)).isEqualTo(antes);
    }

    @Test
    void umaAlteracaoDePdmTerminadaNaoApareceEmCursoERecusaOsAtosPontuais() throws Exception {
        RequestPostProcessor camara = sessao("CM");
        Instrumento pdm = novoPdmEmVigor();
        UUID dinamica = iniciar(pdm, camara, "ALTERACAO");
        Terminada alteracao = new Terminada(pdm, dinamica, "ALTERACAO", null, camara);
        UUID decisao = id(criar(alteracao, "DECISAO_INICIO").andExpect(status().isCreated()));
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '10/2026', data_publicacao_dr = DATE '2026-10-01' "
                + "WHERE id = ?", decisao);
        // A Alteração final publicada fora do assistente: a Dinâmica termina como na consulta à INCM.
        jdbcTemplate.update("UPDATE dinamica SET estado = 'TERMINADA', data_conclusao = now() WHERE id = ?", dinamica);
        int antes = processosDa(alteracao);

        mockMvc.perform(get("/api/v1/instrumentos/" + pdm.getId() + "/disponivel").with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso", empty()));
        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(camara).contentType("application/json")
                        .content(pedido(alteracao, "RETIFICACAO_ATO_ANEXOS", decisao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(ESTADO_INVALIDO));
        criar(alteracao, "ABERTURA_DISCUSSAO_PUBLICA").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(ESTADO_INVALIDO));

        assertThat(processosDa(alteracao)).isEqualTo(antes);
    }

    @Test
    void umTipoDeEntidadeSemPermissaoNaoVeAProrrogacaoERecebe403() throws Exception {
        Terminada mp = medidasPreventivasPublicadas();
        RequestPostProcessor assembleia = sessao("AM");

        mockMvc.perform(get("/api/v1/instrumentos/" + mp.pdm().getId() + "/disponivel").with(assembleia))
                .andExpect(status().isOk())
                .andExpect(jsonPath(dinamicaNoDisponivel(mp), empty()));
        mockMvc.perform(post("/api/v1/dinamicas/" + mp.dinamica() + "/processos").with(assembleia)
                        .contentType("application/json").content(pedido(mp, "PRORROGACAO_MP", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    private Terminada medidasPreventivasPublicadas() throws Exception {
        RequestPostProcessor camara = sessao("CM");
        Instrumento pdm = novoPdmEmVigor();
        UUID dinamica = iniciar(pdm, camara, "MEDIDAS_PREVENTIVAS");
        Terminada semPublicacao = new Terminada(pdm, dinamica, "MEDIDAS_PREVENTIVAS", null, camara);
        UUID publicacao = id(criar(semPublicacao, "PUBLICACAO_MP").andExpect(status().isCreated()));
        Terminada mp = new Terminada(pdm, dinamica, "MEDIDAS_PREVENTIVAS", publicacao, camara);
        publicarPelaIncm(mp, publicacao);
        assertThat(estadoDa(mp)).isEqualTo("TERMINADA");
        return mp;
    }

    /** O que a consulta à INCM faz ao receber a publicação: o processo `PUBLICADO` e a conclusão pelo sistema. */
    private void publicarPelaIncm(Terminada terminada, UUID processo) {
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '20/2026', data_publicacao_dr = DATE '2026-11-02' "
                + "WHERE id = ?", processo);
        transacao.executeWithoutResult(estado -> dinamicaService.verificarConclusaoPeloSistema(
                dinamicaRepository.findById(terminada.dinamica()).orElseThrow()));
    }

    private String dinamicaNoDisponivel(Terminada terminada) {
        return "$.dinamicasEmCurso[?(@.dinamicaId == '" + terminada.dinamica() + "')]";
    }

    private ResultActions criar(Terminada terminada, String tipoProcedimento) throws Exception {
        return mockMvc.perform(post("/api/v1/dinamicas/" + terminada.dinamica() + "/processos").with(terminada.camara())
                .contentType("application/json").content(pedido(terminada, tipoProcedimento, null)));
    }

    private String pedido(Terminada terminada, String tipoProcedimento, UUID processoReferidoId) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", procedimento(terminada.tipoDinamica(), tipoProcedimento));
        if (processoReferidoId != null) {
            corpo.put("processoReferidoId", processoReferidoId);
        }
        return objectMapper.writeValueAsString(corpo);
    }

    private UUID id(ResultActions resultado) throws Exception {
        JsonNode corpo = objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
        return UUID.fromString(corpo.get("id").asText());
    }

    private String estadoDa(Terminada terminada) {
        return jdbcTemplate.queryForObject("SELECT estado FROM dinamica WHERE id = ?", String.class, terminada.dinamica());
    }

    private Timestamp dataConclusao(Terminada terminada) {
        return jdbcTemplate.queryForObject("SELECT data_conclusao FROM dinamica WHERE id = ?", Timestamp.class, terminada.dinamica());
    }

    private Boolean emVigor(Terminada terminada) {
        return jdbcTemplate.queryForObject("SELECT em_vigor FROM instrumento WHERE id = ?", Boolean.class, terminada.pdm().getId());
    }

    private int processosDa(Terminada terminada) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE dinamica_id = ?", Integer.class, terminada.dinamica());
    }

    private RequestPostProcessor sessao(String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-prorrogacao.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + CODIGO_CAMARA_DE_SINTRA, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
    }

    private Instrumento novoPdmEmVigor() {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável", "responsavel-" + UUID.randomUUID() + "@teste-prorrogacao.pt", "INCM"));
        String numero = "PDM-PR-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> "PDM".equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID iniciar(Instrumento instrumento, RequestPostProcessor quem, String tipoDinamica) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoDinamica),
                "descritivo", "Teste " + UUID.randomUUID().toString().substring(0, 8)));
        String resposta = mockMvc.perform(post("/api/v1/instrumentos/" + instrumento.getId() + "/dinamicas").with(quem)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private long procedimento(String tipoDinamica, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.instrumento_dinamica_config_id = ? "
                + "AND tp.codigo = ?", Long.class, dinamicaConfigId(tipoDinamica), tipoProcedimento);
    }

    private long dinamicaConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = ?", Long.class, tipoDinamica);
    }
}
