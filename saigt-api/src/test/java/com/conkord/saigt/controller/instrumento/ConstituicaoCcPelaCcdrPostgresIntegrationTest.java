package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Constituição da Comissão Consultiva criada e submetida pela CCDR, contra o PostgreSQL com os seeds
 * reais do Liquibase: o PDM de Sintra (Câmara de Sintra, entidade 5245) e a CCDR LVT (entidade 8001). Os
 * Instrumentos, as Dinâmicas e os processos nascem pelos endpoints; só a Decisão de início, que a Câmara leva a
 * publicado pela INCM, é gravada diretamente no estado que o cenário pede.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ConstituicaoCcPelaCcdrPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String DICO_SINTRA = "1111";
    /** `incm_act_type_id_serie_2` do Despacho (seed `tipo_ato`). */
    private static final long ID_INCM_DESPACHO = 11L;
    private static final long PART_ID_DA_CCDR = 7_000_000_000_000_008L;
    private static final long PART_ID_DA_CAMARA = 7_000_000_000_000_001L;
    private static final String MENSAGEM_SEM_PERMISSAO_NO_PROCEDIMENTO = "Tipo da entidade da sessão sem permissão neste procedimento.";

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
    private ProcessoRepository processoRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId, Long entidadeId) {
    }

    private record Cenario(UUID instrumentoId, UUID dinamicaId, UUID decisaoId, Sessao camara, Sessao ccdr) {
    }

    // --- Cenário: o PDM de Sintra com a Elaboração aberta e a Decisão de início no estado pedido ---

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte, long partId) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-ccdr.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", partId, "letra", letraParte, "descricao", "Parte " + letraParte),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", ID_INCM_DESPACHO, "nome", "Despacho", "extrato", false))));
        RequestPostProcessor token = jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
        return new Sessao(token, utilizadorId, entidadeId);
    }

    private Sessao camaraDeSintra() {
        return sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H", PART_ID_DA_CAMARA);
    }

    private Sessao ccdrLvt() {
        return sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C", PART_ID_DA_CCDR);
    }

    private UUID id(String json) throws Exception {
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private long procedimentoConfigId(long dinamicaConfigId, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento p ON p.id = c.tipo_procedimento_id "
                + "WHERE c.instrumento_dinamica_config_id = ? AND p.codigo = ?", Long.class, dinamicaConfigId, tipoProcedimento);
    }

    private long tipoAtoDespachoId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class, ID_INCM_DESPACHO);
    }

    private String criarPdmDeSintra(Sessao quem) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PDM"),
                "designacao", "PDM de Sintra " + UUID.randomUUID(), "concelhos", List.of(DICO_SINTRA)));
        return mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String iniciarDinamica(UUID instrumentoId, Sessao quem, String tipoDinamica) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId("PDM", tipoDinamica),
                "descritivo", tipoDinamica + " do PDM de Sintra"));
        return mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumentoId + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** A Câmara cria o PDM e inicia a Elaboração; a Decisão de início fica no estado pedido (a INCM publica-a). */
    private Cenario elaboracaoDeSintra(EstadoProcesso estadoDaDecisaoDeInicio) throws Exception {
        Sessao camara = camaraDeSintra();
        Sessao ccdr = ccdrLvt();
        UUID instrumentoId = id(criarPdmDeSintra(camara));
        UUID dinamicaId = id(iniciarDinamica(instrumentoId, camara, "ELABORACAO"));
        long decisaoConfig = procedimentoConfigId(dinamicaConfigId("PDM", "ELABORACAO"), "DECISAO_INICIO");
        UUID decisaoId = transacao.execute(status -> {
            Processo decisao = new Processo(dinamicaRepository.findById(dinamicaId).orElseThrow(),
                    procedimentoConfigRepository.findById(decisaoConfig).orElseThrow(),
                    utilizadorRepository.findById(camara.utilizadorId()).orElseThrow(),
                    entidadeRepository.findById(camara.entidadeId()).orElseThrow());
            decisao.setOcorrencia(1);
            decisao.setEstado(estadoDaDecisaoDeInicio);
            return processoRepository.saveAndFlush(decisao).getId();
        });
        return new Cenario(instrumentoId, dinamicaId, decisaoId, camara, ccdr);
    }

    private String corpoCriarProcesso(String tipoProcedimento) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "instrumentoProcedimentoConfigId", procedimentoConfigId(dinamicaConfigId("PDM", "ELABORACAO"), tipoProcedimento),
                "tipoAtoId", tipoAtoDespachoId()));
    }

    private int contarProcessosDoProcedimento(UUID dinamicaId, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processo p JOIN instrumento_procedimento_config c "
                + "ON c.id = p.instrumento_procedimento_config_id JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                + "WHERE p.dinamica_id = ? AND t.codigo = ?", Integer.class, dinamicaId, tipoProcedimento);
    }

    private UUID ccdrCriaACc(Cenario cenario) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.ccdr().jwt()).contentType("application/json").content(corpoCriarProcesso("CONSTITUICAO_CC")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andReturn().getResponse().getContentAsString();
        return id(resposta);
    }

    private long versao(UUID processoId) {
        return processoRepository.findById(processoId).map(Processo::getVersao).orElseThrow();
    }

    /** A completude documental é de outra story: aqui só interessa quem submete, por isso os itens ficam opcionais. */
    private void tornarOpcionaisOsDocumentosDaCc() {
        jdbcTemplate.update("UPDATE documento_config SET obrigatorio = false WHERE instrumento_procedimento_config_id = ?",
                procedimentoConfigId(dinamicaConfigId("PDM", "ELABORACAO"), "CONSTITUICAO_CC"));
    }

    private void preencherESubmeter(UUID processoId, Sessao quem) throws Exception {
        String rascunho = objectMapper.writeValueAsString(Map.of("versao", versao(processoId),
                "sumario", "Constituição da Comissão Consultiva do PDM de Sintra.",
                "dataAssinatura", LocalDate.now().toString(), "normaLegal", "Decreto-Lei n.º 80/2015",
                "tipoInformacao", "TEXTO_INTEGRAL",
                "entidadeEmitente", "Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP",
                "elementosAfetados", List.of("PECAS_ESCRITAS")));
        mockMvc.perform(put("/api/v1/processos/" + processoId + "/dados-formulario").with(quem.jwt())
                        .contentType("application/json").content(rascunho))
                .andExpect(status().isOk());
        String submissao = objectMapper.writeValueAsString(Map.of("versao", versao(processoId),
                "sumario", "Constituição da Comissão Consultiva do PDM de Sintra.",
                "dataAssinatura", LocalDate.now().toString(), "normaLegal", "Decreto-Lei n.º 80/2015",
                "tipoInformacao", "TEXTO_INTEGRAL", "confirmacoes", PedidosSubmissao.CONFIRMACOES));
        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/submeter").with(quem.jwt())
                        .contentType("application/json").content(submissao))
                .andExpect(status().isOk());
    }

    // --- Critérios ---

    @Test
    void aCcdrCriaOProcessoDaCcDepoisDaDecisaoDeInicioPublicadaComAEntidadeDaCcdrEOIdusDeSintra() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);

        UUID ccId = ccdrCriaACc(cenario);

        assertThat(jdbcTemplate.queryForObject("SELECT e.codigo FROM processo p JOIN entidade e ON e.id = p.entidade_id "
                + "WHERE p.id = ?", String.class, ccId)).isEqualTo(CODIGO_CCDR_LVT);
        assertThat(processoRepository.findById(ccId).orElseThrow().getEntidade().getId()).isEqualTo(cenario.ccdr().entidadeId());
        assertThat(jdbcTemplate.queryForObject("SELECT criado_por FROM processo WHERE id = ?", UUID.class, ccId))
                .isEqualTo(cenario.ccdr().utilizadorId());
        assertThat(idusProcessoRepository.findById(ccId).orElseThrow().getIdus()).startsWith("01." + DICO_SINTRA + ".");
    }

    @Test
    void aCcdrNaoCriaACcComADecisaoDeInicioAindaEmValidacaoENadaFicaCriado() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.ENVIADO_DGT);

        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.ccdr().jwt()).contentType("application/json").content(corpoCriarProcesso("CONSTITUICAO_CC")))
                .andExpect(status().isConflict());

        assertThat(contarProcessosDoProcedimento(cenario.dinamicaId(), "CONSTITUICAO_CC")).isZero();
    }

    @Test
    void aCcdrSubmeteACcParaAValidacaoDaDgtComOPartIdDaParteCDaSuaAdesao() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);
        tornarOpcionaisOsDocumentosDaCc();
        UUID ccId = ccdrCriaACc(cenario);

        preencherESubmeter(ccId, cenario.ccdr());

        Processo submetido = processoRepository.findById(ccId).orElseThrow();
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(submetido.getDrParteId()).isEqualTo(PART_ID_DA_CCDR);
    }

    @Test
    void umaSessaoDaCcdrSemParteCNaoCriaACc() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);
        Sessao ccdrComParteH = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "H", PART_ID_DA_CAMARA);

        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(ccdrComParteH.jwt()).contentType("application/json").content(corpoCriarProcesso("CONSTITUICAO_CC")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PARTE_DR_NAO_AUTORIZADA"));

        assertThat(contarProcessosDoProcedimento(cenario.dinamicaId(), "CONSTITUICAO_CC")).isZero();
    }

    @Test
    void aCamaraDeSintraNaoCriaOProcessoDaCc() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);

        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.camara().jwt()).contentType("application/json").content(corpoCriarProcesso("CONSTITUICAO_CC")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_SEM_PERMISSAO_NO_PROCEDIMENTO));

        assertThat(contarProcessosDoProcedimento(cenario.dinamicaId(), "CONSTITUICAO_CC")).isZero();
    }

    @Test
    void aCcdrNaoCriaUmPdmNemIniciaAsDinamicasDoPdmDeSintra() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);
        int instrumentosAntes = jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento", Integer.class);
        int dinamicasAntes = jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica", Integer.class);

        String novoPdm = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PDM"),
                "designacao", "PDM da CCDR " + UUID.randomUUID(), "concelhos", List.of(DICO_SINTRA)));
        mockMvc.perform(post(URL_INSTRUMENTOS).with(cenario.ccdr().jwt()).contentType("application/json").content(novoPdm))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão para o tipo de Instrumento."));
        String revisao = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId("PDM", "REVISAO"),
                "descritivo", "Revisão do PDM de Sintra"));
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + cenario.instrumentoId() + "/dinamicas").with(cenario.ccdr().jwt())
                        .contentType("application/json").content(revisao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica."));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento", Integer.class)).isEqualTo(instrumentosAntes);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica", Integer.class)).isEqualTo(dinamicasAntes);
    }

    @Test
    void aCamaraDeSintraCriaOPdmEIniciaAElaboracaoSemRegressao() throws Exception {
        Sessao camara = camaraDeSintra();

        UUID instrumentoId = id(criarPdmDeSintra(camara));
        UUID dinamicaId = id(iniciarDinamica(instrumentoId, camara, "ELABORACAO"));

        assertThat(dinamicaId).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM dinamica WHERE id = ?", String.class, dinamicaId))
                .isEqualTo("EM_CURSO");
    }

    @Test
    void aCcdrNaoTemDinamicasParaIniciarENaElaboracaoAbertaSoVeAConstituicaoDaCc() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);

        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + cenario.instrumentoId() + "/disponivel").with(cenario.ccdr().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", hasSize(0)))
                .andExpect(jsonPath("$.dinamicasEmCurso", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].tipoProcedimento",
                        contains("CONSTITUICAO_CC")));
    }

    @Test
    void aCamaraVeACcDaCcdrEmLeituraEAAberturaDaDiscussaoPublicaSoSeguePublicadaACc() throws Exception {
        Cenario cenario = elaboracaoDeSintra(EstadoProcesso.PUBLICADO);

        // Antes da CC: o sequenciamento por ordem recusa a Abertura da Discussão Pública.
        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.camara().jwt()).contentType("application/json")
                        .content(corpoCriarProcesso("ABERTURA_DISCUSSAO_PUBLICA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEQUENCIAMENTO_NAO_CUMPRIDO"));

        tornarOpcionaisOsDocumentosDaCc();
        UUID ccId = ccdrCriaACc(cenario);
        preencherESubmeter(ccId, cenario.ccdr());

        mockMvc.perform(get("/api/v1/processos/" + ccId).with(cenario.camara().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis", hasSize(0)));
        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.camara().jwt()).contentType("application/json")
                        .content(corpoCriarProcesso("ABERTURA_DISCUSSAO_PUBLICA")))
                .andExpect(status().isConflict());
        assertThat(contarProcessosDoProcedimento(cenario.dinamicaId(), "ABERTURA_DISCUSSAO_PUBLICA")).isZero();

        // Com a CC publicada, a Câmara segue para a Abertura da Discussão Pública.
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", ccId);
        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamicaId() + "/processos")
                        .with(cenario.camara().jwt()).contentType("application/json")
                        .content(corpoCriarProcesso("ABERTURA_DISCUSSAO_PUBLICA")))
                .andExpect(status().isCreated());
    }
}
