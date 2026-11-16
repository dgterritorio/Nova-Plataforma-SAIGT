package com.conkord.saigt.db;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.GetActDetailsResponse;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.ConsultaIncmService;
import com.conkord.saigt.service.incm.DesfechoConsultaIncm;
import com.conkord.saigt.service.instrumento.CaducidadeDinamicaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Prorrogação do ato contra o PostgreSQL migrado pelo {@code master.xml}: os itens documentais do seed, a Informação
 * Base do Texto do ato na estrutura documental e no formulário, e o recálculo da data de caducidade da Dinâmica pela
 * publicação da INCM (cliente da INCM simulado, o resto real) e pelo registo externo.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class CaducidadeProrrogacaoAtoPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String TEXTO_ATO = "Texto do Ato";
    private static final String DELIBERACAO = "Deliberação";

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

    @MockBean
    private ClienteIncm clienteIncm;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConsultaIncmService consultaIncmService;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private final ListAppender<ILoggingEvent> registos = new ListAppender<>();

    @BeforeEach
    void ouvirOLog() {
        registos.start();
        ((Logger) LoggerFactory.getLogger(CaducidadeDinamicaService.class)).addAppender(registos);
    }

    @AfterEach
    void deixarDeOuvirOLog() {
        ((Logger) LoggerFactory.getLogger(CaducidadeDinamicaService.class)).detachAppender(registos);
    }

    /** Uma Alteração de PDM com a Decisão de início publicada a 2017-11-01 e a data de caducidade a 2018-11-01. */
    private record Alteracao(UUID dinamica, UUID decisao, RequestPostProcessor camara) {
    }

    private static final String ITENS_PRORROGACAO_ATO = "documento_config d JOIN instrumento_procedimento_config c "
            + "ON c.id = d.instrumento_procedimento_config_id JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "WHERE t.codigo = 'PRORROGACAO_ATO'";

    @Test
    void cadaProrrogacaoDoAtoAtivaTemOsQuatroItensComAInformacaoBaseSoNoTextoDoAto() {
        Integer configuracoes = jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id WHERE t.codigo = 'PRORROGACAO_ATO' AND c.ativo",
                Integer.class);
        assertThat(configuracoes).isPositive();

        List<String> porConfiguracao = jdbcTemplate.queryForList("SELECT string_agg(d.codigo || '/' || d.separador || '/' "
                + "|| d.categoria || '/' || d.tipo_elemento || '/' || d.formato || '/' || d.obrigatorio || '/' "
                + "|| coalesce(d.elemento_informacao_base, '-'), ',' ORDER BY d.ordem) FROM " + ITENS_PRORROGACAO_ATO
                + " AND c.ativo GROUP BY c.id", String.class);

        assertThat(porConfiguracao).hasSize(configuracoes).containsOnly(
                "TEXTO_ATO_PUB/Texto do Ato/PUBLICACAO/PECA_ESCRITA/EDITAVEL/true/TEXTO_ATO,"
                        + "TEXTO_ATO_DEP/Texto do Ato/DEPOSITO/PECA_ESCRITA/NAO_EDITAVEL/false/TEXTO_ATO,"
                        + "DELIBERACAO_PUB/Deliberação/PUBLICACAO/PECA_ESCRITA/EDITAVEL/true/-,"
                        + "DELIBERACAO_DEP/Deliberação/DEPOSITO/PECA_ESCRITA/NAO_EDITAVEL/false/-");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT d.nome FROM " + ITENS_PRORROGACAO_ATO + " ORDER BY 1",
                String.class)).containsExactly("Deliberação de aprovação da prorrogação",
                "Texto do ato a publicar no Diário da República");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + ITENS_PRORROGACAO_ATO
                + " AND d.link_publico_permitido <> (d.categoria = 'PUBLICACAO')", Integer.class)).isZero();
    }

    @Test
    void aEstruturaDocumentalDaProrrogacaoTrazAInformacaoBaseNoTextoDoAtoEOFormularioGuardaOPrazo() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID prorrogacao = criarProrrogacao(alteracao);

        // As peças escritas como elemento afetado: sem elas a estrutura documental não mostra os itens.
        Long versao = jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, prorrogacao);
        mockMvc.perform(put("/api/v1/processos/" + prorrogacao + "/dados-formulario").with(alteracao.camara())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("versao", versao,
                                "elementosAfetados", List.of("PECAS_ESCRITAS"),
                                "informacaoBase", Map.of("textoAto", Map.of("prazoMeses", 6, "avaliacaoAmbiental", false))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + prorrogacao + "/estrutura-documental").with(alteracao.camara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador", contains(TEXTO_ATO, DELIBERACAO)))
                .andExpect(jsonPath("$.separadores[0].elementoInformacaoBase").value("TEXTO_ATO"))
                .andExpect(jsonPath("$.separadores[1].elementoInformacaoBase").doesNotExist())
                .andExpect(jsonPath("$.separadores[0].publicacao[*].codigo", contains("TEXTO_ATO_PUB")))
                .andExpect(jsonPath("$.separadores[1].deposito[*].codigo", contains("DELIBERACAO_DEP")))
                .andExpect(jsonPath("$.pendencias[*].codigo", containsInAnyOrder("TEXTO_ATO_PUB", "DELIBERACAO_PUB")));

        assertThat(jdbcTemplate.queryForObject("SELECT dados_formulario::jsonb #>> '{informacaoBase,textoAto,prazoMeses}' "
                + "FROM processo WHERE id = ?", String.class, prorrogacao)).isEqualTo("6");
    }

    @Test
    void aProrrogacaoPublicadaPelaIncmRecalculaADataEASeguinteVoltaARecalcular() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();

        UUID primeira = criarProrrogacao(alteracao);
        assertThat(publicarPelaIncm(primeira, LocalDate.of(2018, 11, 1), "{\"prazoAnos\":1}"))
                .isEqualTo(DesfechoConsultaIncm.PUBLICADO);
        assertThat(dataCaducidade(alteracao)).isEqualTo(LocalDate.of(2019, 11, 1));

        UUID segunda = criarProrrogacao(alteracao);
        publicarPelaIncm(segunda, LocalDate.of(2019, 6, 1), "{\"prazoMeses\":6}");
        assertThat(dataCaducidade(alteracao)).isEqualTo(LocalDate.of(2019, 12, 1));
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM dinamica WHERE id = ?", String.class, alteracao.dinamica()))
                .isEqualTo("EM_CURSO");
        assertThat(avisos()).isEmpty();
    }

    @Test
    void aProrrogacaoPublicadaPelaIncmLimpaOsAlertasQueDeixamDeEstarAtingidos() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        LocalDate hoje = LocalDate.now(ZoneId.of("Europe/Lisbon"));
        jdbcTemplate.update("UPDATE dinamica SET data_caducidade = ?, alertas_enviados = '[\"UM_MES\", \"QUINZE_DIAS\"]'::jsonb "
                + "WHERE id = ?", hoje.plusDays(10), alteracao.dinamica());

        publicarPelaIncm(criarProrrogacao(alteracao), hoje, "{\"prazoMeses\":6}");

        assertThat(dataCaducidade(alteracao)).isEqualTo(hoje.plusMonths(6));
        assertThat(jdbcTemplate.queryForObject("SELECT alertas_enviados::text FROM dinamica WHERE id = ?", String.class,
                alteracao.dinamica())).isEqualTo("[]");
    }

    @Test
    void aProrrogacaoPublicadaPelaIncmSemPrazoNaoMudaADataEConcluiAPublicacao() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID prorrogacao = criarProrrogacao(alteracao);

        assertThat(publicarPelaIncm(prorrogacao, LocalDate.of(2018, 11, 1), "{\"prazoAnos\":0,\"prazoMeses\":0,\"prazoDias\":0}"))
                .isEqualTo(DesfechoConsultaIncm.PUBLICADO);

        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM processo WHERE id = ?", String.class, prorrogacao))
                .isEqualTo("PUBLICADO");
        assertThat(dataCaducidade(alteracao)).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem prazo do Texto do ato")
                .contains("processoId=" + prorrogacao).contains("dinamicaId=" + alteracao.dinamica());
    }

    @Test
    void aProrrogacaoRegistadaExternamenteNaoMudaADataEDeixaOAviso() throws Exception {
        Alteracao alteracao = alteracaoComDecisaoPublicada();
        UUID master = utilizadorMaster();
        Map<String, Object> pedido = Map.of("instrumentoProcedimentoConfigId", procedimento("PRORROGACAO_ATO"),
                "entidadeId", entidadeDeSintra(), "numeroDr", "2018/DR/335", "dataPublicacaoDr", "2018-11-01",
                "motivo", "Publicada fora da plataforma");

        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + alteracao.dinamica() + "/processos/registo-externo")
                        .with(jwt().jwt(b -> b.claim(SUB, master.toString()).claim("origem", "AD_DGT")
                                .claim("perfil_interno", "MASTER")))
                        .contentType("application/json").content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andReturn().getResponse().getContentAsString();
        String processoId = objectMapper.readTree(resposta).get("id").asText();

        assertThat(dataCaducidade(alteracao)).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem prazo do Texto do ato")
                .contains("processoId=" + processoId).contains("dinamicaId=" + alteracao.dinamica());
    }

    // ---------- Apoio ----------

    private List<String> avisos() {
        return registos.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private LocalDate dataCaducidade(Alteracao alteracao) {
        return jdbcTemplate.queryForObject("SELECT data_caducidade FROM dinamica WHERE id = ?", LocalDate.class,
                alteracao.dinamica());
    }

    /** O processo à espera da INCM, com o prazo no Texto do ato; a consulta é a real, com a resposta da INCM simulada. */
    private DesfechoConsultaIncm publicarPelaIncm(UUID processo, LocalDate dataPublicacao, String textoAto) {
        long actIdentifier = 900_000_000_335_000L + Math.floorMod(processo.hashCode(), 1000);
        jdbcTemplate.update("UPDATE processo SET estado = 'EM_VALIDACAO_INCM', incm_act_identifier = ?, "
                        + "dados_formulario = ?::jsonb WHERE id = ?", String.valueOf(actIdentifier),
                "{\"informacaoBase\":{\"textoAto\":" + textoAto + "}}", processo);
        GetActDetailsResponse.Publication publicacao = new GetActDetailsResponse.Publication(null, "Prorrogação",
                "https://exemplo.pt/dr/" + actIdentifier, "Diário da República", dataPublicacao);
        GetActDetailsResponse detalhes = new GetActDetailsResponse(true, null, "Published", null, null,
                "DR-" + actIdentifier, null, null, "Published", List.of(), List.of(), publicacao);
        when(clienteIncm.obterDetalhesAto(eq(processo), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, detalhes, null, 5L));
        return consultaIncmService.consultar(processo);
    }

    private Alteracao alteracaoComDecisaoPublicada() throws Exception {
        RequestPostProcessor camara = sessao();
        Instrumento pdm = novoPdmEmVigor();
        String pedido = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(),
                "descritivo", "Alteração de teste"));
        UUID dinamica = idDe(mockMvc.perform(post("/api/v1/instrumentos/" + pdm.getId() + "/dinamicas").with(camara)
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID decisao = idDe(mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(camara)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoProcedimentoConfigId",
                                procedimento("DECISAO_INICIO")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '10/2017', "
                + "data_publicacao_dr = DATE '2017-11-01' WHERE id = ?", decisao);
        jdbcTemplate.update("UPDATE dinamica SET data_caducidade = DATE '2018-11-01' WHERE id = ?", dinamica);
        return new Alteracao(dinamica, decisao, camara);
    }

    private UUID criarProrrogacao(Alteracao alteracao) throws Exception {
        return idDe(mockMvc.perform(post("/api/v1/dinamicas/" + alteracao.dinamica() + "/processos").with(alteracao.camara())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoProcedimentoConfigId",
                                procedimento("PRORROGACAO_ATO"), "processoReferidoId", alteracao.decisao()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processoReferidoId").value(alteracao.decisao().toString()))
                .andReturn().getResponse().getContentAsString());
    }

    private UUID idDe(String resposta) throws Exception {
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private Long entidadeDeSintra() {
        return jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, CODIGO_CAMARA_DE_SINTRA);
    }

    private RequestPostProcessor sessao() {
        Long entidadeId = entidadeDeSintra();
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-prorrogacao.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", "CM", "nif", nipc,
                "nome", "Entidade de teste " + CODIGO_CAMARA_DE_SINTRA, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
    }

    private UUID utilizadorMaster() {
        Utilizador master = new Utilizador("Master DGT", "master-" + UUID.randomUUID() + "@teste-prorrogacao.pt", "AD_DGT");
        master.setPerfilInterno(com.conkord.saigt.persistence.operacional.PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(master).getId();
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
