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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Atos pontuais de «Outros atos e anexos» sobre a Alteração de PDM, contra o PostgreSQL migrado pelo {@code master.xml}
 * (os 5 atos pontuais e as permissões vêm do seed): o ato visado na criação, fora do sequenciamento e da regra do
 * processo em curso, a ocorrência seguinte, o `/disponivel`, o detalhe, o painel e a Dinâmica que não termina.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class AtoPontualPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String REFERENCIA_INVALIDA = "REFERENCIA_INVALIDA";

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

    /** Uma Alteração de PDM com a Decisão de início publicada (DR 10/2026, 2026-10-01). */
    private record Alteracao(Instrumento pdm, UUID dinamica, UUID decisao, RequestPostProcessor camara) {
    }

    @Test
    void aMigracaoCriaAColunaAChaveEstrangeiraEOIndiceDoProcessoReferido() {
        assertThat(jdbcTemplate.queryForObject("SELECT data_type || ':' || is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'processo' AND column_name = 'processo_referido_id'", String.class)).isEqualTo("uuid:YES");
        assertThat(jdbcTemplate.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'fk_processo_processo_referido'", String.class))
                .isEqualTo("FOREIGN KEY (processo_referido_id) REFERENCES processo(id)");
        assertThat(jdbcTemplate.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_processo_processo_referido'",
                String.class)).contains("ON public.processo USING btree (processo_referido_id)");
    }

    @Test
    void aCamaraCriaUmaRetificacaoDaDecisaoDeInicioPublicadaForaDoSequenciamento() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();

        // A Discussão pública (obrigatória) está por publicar: a Alteração (final) fica bloqueada, o ato pontual não.
        criar(alteracao, "ALTERACAO", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEQUENCIAMENTO_NAO_CUMPRIDO"));
        JsonNode criado = corpo(criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.processoReferidoId").value(alteracao.decisao().toString()))
                .andExpect(jsonPath("$.ocorrencia").value(1)));

        assertThat(jdbcTemplate.queryForObject("SELECT processo_referido_id FROM processo WHERE id = ?", UUID.class,
                UUID.fromString(criado.get("id").asText()))).isEqualTo(alteracao.decisao());
    }

    @Test
    void oAtoPontualCriaSeComOutroProcessoDaDinamicaEnviadoADgt() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID discussao = UUID.fromString(corpo(criar(alteracao, "ABERTURA_DISCUSSAO_PUBLICA", null)
                .andExpect(status().isCreated())).get("id").asText());
        jdbcTemplate.update("UPDATE processo SET estado = 'ENVIADO_DGT' WHERE id = ?", discussao);

        criar(alteracao, "ALTERACAO_ATO_ANEXOS", alteracao.decisao()).andExpect(status().isCreated());
        // A Discussão pública enviada à DGT não é um ato publicado: não se visa.
        criar(alteracao, "REVOGACAO_ATO", discussao).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(REFERENCIA_INVALIDA));
    }

    @Test
    void semAtoReferidoOuComUmInvalidoRecusaCom400ReferenciaInvalidaSemGravarNada() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        Alteracao outra = alteracaoComDecisaoPublicada();
        UUID emSubmissao = UUID.fromString(corpo(criar(alteracao, "ABERTURA_DISCUSSAO_PUBLICA", null)
                .andExpect(status().isCreated())).get("id").asText());
        UUID constituicaoCc = processoPublicadoDeOutroTipo(alteracao, "CONSTITUICAO_CC");
        int antes = processosDa(alteracao);

        criar(alteracao, "RETIFICACAO_ATO_ANEXOS", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(REFERENCIA_INVALIDA))
                .andExpect(jsonPath("$.mensagem").value("Ato referido inválido."))
                .andExpect(jsonPath("$.campos[0].path").value("processoReferidoId"))
                .andExpect(jsonPath("$.traceId").exists());
        for (UUID invalido : List.of(outra.decisao(), emSubmissao, constituicaoCc, UUID.randomUUID())) {
            criar(alteracao, "RETIFICACAO_ATO_ANEXOS", invalido).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value(REFERENCIA_INVALIDA));
        }

        assertThat(processosDa(alteracao)).isEqualTo(antes);
    }

    @Test
    void umProcedimentoNormalComAtoReferidoRecusaCom400() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();

        criar(alteracao, "ABERTURA_DISCUSSAO_PUBLICA", alteracao.decisao()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(REFERENCIA_INVALIDA))
                .andExpect(jsonPath("$.campos[0].path").value("processoReferidoId"));
        assertThat(processosDa(alteracao)).isEqualTo(1);
    }

    @Test
    void outraRetificacaoComAPrimeiraEmCursoDa409EDepoisDePublicadaCriaAOcorrenciaSeguinte() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID primeira = UUID.fromString(corpo(criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao())
                .andExpect(status().isCreated())).get("id").asText());

        criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"))
                .andExpect(jsonPath("$.mensagem").value("Já existe um processo em curso deste ato."));

        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", primeira);
        criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrencia").value(2));
    }

    @Test
    void umTipoDeEntidadeSemPermissaoNoAtoPontualRecebe403() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        RequestPostProcessor assembleia = sessao("AM");

        mockMvc.perform(post("/api/v1/dinamicas/" + alteracao.dinamica() + "/processos").with(assembleia)
                        .contentType("application/json").content(pedido("RETIFICACAO_ATO_ANEXOS", alteracao.decisao())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void aDecisaoDeInicioRegistadaExternamenteEUmAtoQueSeVisa() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        jdbcTemplate.update("UPDATE processo SET origem_dados = 'REGISTO_EXTERNO' WHERE id = ?", alteracao.decisao());

        criar(alteracao, "PRORROGACAO_ATO", alteracao.decisao()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.processoReferidoId").value(alteracao.decisao().toString()));
    }

    @Test
    void oDisponivelTrazOsAtosPontuaisComADecisaoDeInicioSoQuandoHaUmAtoPublicado() throws Exception {
        RequestPostProcessor camara = sessao("CM");
        Instrumento pdm = novoPdmEmVigor();
        UUID dinamica = iniciar(pdm, camara);
        String pontuais = "$.dinamicasEmCurso[0].procedimentosDisponiveis[?(@.atoPontual == true)]";

        mockMvc.perform(get("/api/v1/instrumentos/" + pdm.getId() + "/disponivel").with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath(pontuais, empty()));

        UUID decisao = criarEPublicarDecisao(dinamica, camara);
        String numeroDecisao = jdbcTemplate.queryForObject("SELECT numero_processo FROM processo WHERE id = ?", String.class, decisao);

        mockMvc.perform(get("/api/v1/instrumentos/" + pdm.getId() + "/disponivel").with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath(pontuais + ".tipoProcedimento", containsInAnyOrder("ALTERACAO_ATO_ANEXOS",
                        "RETIFICACAO_ATO_ANEXOS", "REVOGACAO_ATO", "PRORROGACAO_ATO", "DECLARACAO_NULIDADE_JUDICIAL")))
                .andExpect(jsonPath(pontuais + ".atosReferiveis[*].processoId", everyItem(is(decisao.toString()))))
                .andExpect(jsonPath(pontuais + ".atosReferiveis[*].numeroProcesso", everyItem(is(numeroDecisao))))
                .andExpect(jsonPath(pontuais + ".atosReferiveis[*].tipoProcedimento", everyItem(is("DECISAO_INICIO"))))
                .andExpect(jsonPath(pontuais + ".atosReferiveis[*].numeroDr", everyItem(is("10/2026"))))
                .andExpect(jsonPath(pontuais + ".atosReferiveis[*].dataPublicacaoDr", hasSize(5)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[?(@.atoPontual == false)].atosReferiveis[*]",
                        empty()));
    }

    @Test
    void umAtoPontualPublicadoPelaIncmNaoTerminaADinamica() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID retificacao = UUID.fromString(corpo(criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao())
                .andExpect(status().isCreated())).get("id").asText());

        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", retificacao);
        transacao.executeWithoutResult(estado -> dinamicaService.verificarConclusaoPeloSistema(
                dinamicaRepository.findById(alteracao.dinamica()).orElseThrow()));

        assertThat(dinamicaRepository.findById(alteracao.dinamica()).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
    }

    @Test
    void oDetalheEOPainelMostramOAtoPontualEOProcessoVisado() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        String numeroDecisao = jdbcTemplate.queryForObject("SELECT numero_processo FROM processo WHERE id = ?", String.class,
                alteracao.decisao());
        UUID retificacao = UUID.fromString(corpo(criar(alteracao, "RETIFICACAO_ATO_ANEXOS", alteracao.decisao())
                .andExpect(status().isCreated())).get("id").asText());

        mockMvc.perform(get("/api/v1/processos/" + retificacao).with(alteracao.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atoPontual").value(true))
                .andExpect(jsonPath("$.processoReferidoId").value(alteracao.decisao().toString()))
                .andExpect(jsonPath("$.numeroProcessoReferido").value(numeroDecisao))
                .andExpect(jsonPath("$.tipoProcedimentoReferido").value("DECISAO_INICIO"));
        mockMvc.perform(get("/api/v1/processos/" + alteracao.decisao()).with(alteracao.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atoPontual").value(false))
                .andExpect(jsonPath("$.processoReferidoId").doesNotExist())
                .andExpect(jsonPath("$.numeroProcessoReferido").doesNotExist());

        String procedimento = "$.dinamicas[?(@.id == '" + alteracao.dinamica() + "')].procedimentos[?(@.tipoProcedimento == '%s')]";
        mockMvc.perform(get("/api/v1/instrumentos/" + alteracao.pdm().getId()).with(alteracao.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(String.format(procedimento, "RETIFICACAO_ATO_ANEXOS") + ".atoPontual", containsInAnyOrder(true)))
                .andExpect(jsonPath(String.format(procedimento, "RETIFICACAO_ATO_ANEXOS") + ".processos[*].numeroProcessoReferido",
                        containsInAnyOrder(numeroDecisao)))
                .andExpect(jsonPath(String.format(procedimento, "RETIFICACAO_ATO_ANEXOS") + ".processos[*].processoReferidoId",
                        containsInAnyOrder(alteracao.decisao().toString())))
                .andExpect(jsonPath(String.format(procedimento, "DECISAO_INICIO") + ".atoPontual", containsInAnyOrder(false)))
                .andExpect(jsonPath(String.format(procedimento, "DECISAO_INICIO") + ".processos[*].numeroProcessoReferido",
                        not(containsInAnyOrder(numeroDecisao))));
    }

    private Alteracao alteracaoComDecisaoPublicada() throws Exception {
        RequestPostProcessor camara = sessao("CM");
        Instrumento pdm = novoPdmEmVigor();
        UUID dinamica = iniciar(pdm, camara);
        return new Alteracao(pdm, dinamica, criarEPublicarDecisao(dinamica, camara), camara);
    }

    private UUID criarEPublicarDecisao(UUID dinamica, RequestPostProcessor camara) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(camara)
                        .contentType("application/json").content(pedido("DECISAO_INICIO", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID decisao = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '10/2026', data_publicacao_dr = DATE '2026-10-01' "
                + "WHERE id = ?", decisao);
        return decisao;
    }

    /** Um processo `PUBLICADO` de um tipo de Procedimento fora da Decisão de início e da Discussão pública, na Dinâmica. */
    private UUID processoPublicadoDeOutroTipo(Alteracao alteracao, String tipoProcedimento) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por, ocorrencia, "
                        + "numero_processo, entidade_id, estado) VALUES (?, ?, (SELECT min(ipc.id) FROM instrumento_procedimento_config ipc "
                        + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE tp.codigo = ?), "
                        + "(SELECT criado_por FROM processo WHERE id = ?), 1, ?, (SELECT entidade_id FROM processo WHERE id = ?), 'PUBLICADO')",
                id, alteracao.dinamica(), tipoProcedimento, alteracao.decisao(),
                String.valueOf(90_000 + Math.floorMod(id.hashCode(), 10_000)), alteracao.decisao());
        return id;
    }

    private ResultActions criar(Alteracao alteracao, String tipoProcedimento, UUID processoReferidoId) throws Exception {
        return mockMvc.perform(post("/api/v1/dinamicas/" + alteracao.dinamica() + "/processos").with(alteracao.camara())
                .contentType("application/json").content(pedido(tipoProcedimento, processoReferidoId)));
    }

    private String pedido(String tipoProcedimento, UUID processoReferidoId) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", procedimento(tipoProcedimento));
        if (processoReferidoId != null) {
            corpo.put("processoReferidoId", processoReferidoId);
        }
        return objectMapper.writeValueAsString(corpo);
    }

    private JsonNode corpo(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private int processosDa(Alteracao alteracao) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE dinamica_id = ?", Integer.class, alteracao.dinamica());
    }

    private RequestPostProcessor sessao(String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-ato-pontual.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + CODIGO_CAMARA_DE_SINTRA, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
    }

    private Instrumento novoPdmEmVigor() {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável", "responsavel-" + UUID.randomUUID() + "@teste-ato-pontual.pt", "INCM"));
        String numero = "PDM-AP-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> "PDM".equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID iniciar(Instrumento instrumento, RequestPostProcessor quem) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(),
                "descritivo", "Alteração de teste"));
        String resposta = mockMvc.perform(post("/api/v1/instrumentos/" + instrumento.getId() + "/dinamicas").with(quem)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private long procedimento(String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.instrumento_dinamica_config_id = ? "
                + "AND tp.codigo = ?", Long.class, dinamicaConfigId(), tipoProcedimento);
    }

    private long dinamicaConfigId() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = 'ALTERACAO'", Long.class);
    }
}
