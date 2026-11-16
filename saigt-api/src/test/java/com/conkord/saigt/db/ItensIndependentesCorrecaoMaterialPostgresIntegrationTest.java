package com.conkord.saigt.db;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os itens independentes dos elementos afetados (US-259) contra o PostgreSQL migrado pelo {@code master.xml}: a
 * estrutura documental com só peças gráficas afetadas e a recusa e o sucesso da submissão. A lista da DGT não tem as
 * provas de notificação da Correção Material e prevalece (FR-193), por isso não há seed: o teste cria-as como o
 * backoffice as criaria.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ItensIndependentesCorrecaoMaterialPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String PROVA_AM = "PROVA_NOTIFICACAO_AM_DEP";
    private static final String PROVA_CCDR = "PROVA_NOTIFICACAO_CCDR_DEP";
    private static final String SEPARADOR = "Provas de notificação";
    private static final String HASH = "a".repeat(64);
    private static final String CAMPOS_DO_FORMULARIO = "\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
            + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";

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
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    /** As duas provas de notificação, independentes dos elementos afetados, na Correção Material de PDM, PP e PU. */
    @BeforeEach
    void criarAsProvasComoNoBackoffice() {
        jdbcTemplate.update("INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, separador, nome, "
                + "categoria, tipo_elemento, formato, obrigatorio, extensoes_aceites, ordem, ativo, "
                + "independente_elementos_afetados, tamanho_maximo_ficheiro, tamanho_maximo_total, numero_maximo_ficheiros) "
                + "SELECT ipc.id, item.codigo, ?, item.nome, 'DEPOSITO', 'OUTROS', 'NAO_EDITAVEL', true, "
                + "'[\"pdf\",\"jpg\",\"png\"]'::jsonb, item.ordem, true, true, 524288000, 2147483648, 10 "
                + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "CROSS JOIN (VALUES (?, 'Prova de notificação prévia à Assembleia Municipal', 90), "
                + "(?, 'Prova de notificação prévia à CCDR competente', 91)) AS item(codigo, nome, ordem) "
                + "WHERE tp.codigo = 'CORRECAO_MATERIAL' AND ti.codigo IN ('PDM', 'PP', 'PU') AND NOT EXISTS (SELECT 1 "
                + "FROM documento_config d WHERE d.instrumento_procedimento_config_id = ipc.id AND d.codigo = item.codigo)",
                SEPARADOR, PROVA_AM, PROVA_CCDR);
    }

    // --- estrutura documental ---

    @Test
    void naCorrecaoMaterialDePuSoComPecasGraficasAsProvasAparecemComoObrigatoriasNoSeuSeparador() throws Exception {
        Sessao camara = camaraDeSintra();
        UUID processo = processoComElementos("PU", "CORRECAO_MATERIAL", "CORRECAO_MATERIAL", camara, "[\"PECAS_GRAFICAS\"]");

        // Com a lista da DGT (FR-193) a Correção Material de PU tem outros separadores; as provas ficam no seu.
        String provas = "$.separadores[?(@.separador == '" + SEPARADOR + "')]";
        mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(hasItem(SEPARADOR)))
                .andExpect(jsonPath(provas + ".publicacao[*]").isEmpty())
                .andExpect(jsonPath(provas + ".deposito[*].codigo").value(containsInAnyOrder(PROVA_AM, PROVA_CCDR)))
                .andExpect(jsonPath(provas + ".deposito[*].obrigatorio").value(everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath(provas + ".deposito[*].tipoElemento").value(everyItem(org.hamcrest.Matchers.is("OUTROS"))))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(hasItems(PROVA_AM, PROVA_CCDR)));
    }

    @Test
    void naDecisaoDeInicioDaAlteracaoDePdmSoComPecasGraficasAsProvasNaoAparecem() throws Exception {
        // A lista da DGT (FR-193) dá a esta Decisão de início só o Texto do ato e a Deliberação, com a Informação Base,
        // que aparecem sempre; as provas são só da Correção Material.
        Sessao camara = camaraDeSintra();
        UUID processo = processoComElementos("PDM", "ALTERACAO", "DECISAO_INICIO", camara, "[\"PECAS_GRAFICAS\"]");

        String resposta = mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(camara.jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> codigos = new ArrayList<>();
        for (JsonNode separador : objectMapper.readTree(resposta).get("separadores")) {
            for (String caixa : List.of("publicacao", "deposito")) {
                separador.get(caixa).forEach(item -> codigos.add(item.get("codigo").asText()));
            }
        }
        assertThat(codigos).isNotEmpty().doesNotContain(PROVA_AM, PROVA_CCDR);
    }

    // --- submissão ---

    @Test
    void aCorrecaoMaterialDePdmSoComPecasGraficasNaoSeSubmeteSemAsProvasESubmeteComElas() throws Exception {
        Sessao camara = camaraDeSintra();
        UUID processo = processoComElementos("PDM", "CORRECAO_MATERIAL", "CORRECAO_MATERIAL", camara, "[\"PECAS_GRAFICAS\"]");

        submeter(processo, camara)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItems(
                        "documentos." + PROVA_AM, "documentos." + PROVA_CCDR)));
        assertThat(estado(processo)).isEqualTo("EM_SUBMISSAO");

        // As plantas obrigatórias da lista da DGT (FR-193): com elas, só faltam as provas.
        for (String codigo : pendenciasForaDasProvas(processo, camara)) {
            carregarProva(processo, camara, codigo);
        }
        submeter(processo, camara)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder(
                        "documentos." + PROVA_AM, "documentos." + PROVA_CCDR)));

        carregarProva(processo, camara, PROVA_AM);
        submeter(processo, camara)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder("documentos." + PROVA_CCDR)));

        carregarProva(processo, camara, PROVA_CCDR);
        // Com peças gráficas a publicar, a Correção Material (procedimento final) exige também a Série MOTU.
        completarSerieMotu(processo, camara);
        submeter(processo, camara)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(estado(processo)).isEqualTo("ENVIADO_DGT");
    }

    @Test
    void umDocxNumaProvaERecusadoPelaRegraDeExtensoes() throws Exception {
        Sessao camara = camaraDeSintra();
        UUID processo = processoComElementos("PDM", "CORRECAO_MATERIAL", "CORRECAO_MATERIAL", camara, "[\"PECAS_GRAFICAS\"]");

        // O leitor de uploads lê o corpo em streaming: um multipart real, com as partes de texto antes do ficheiro.
        String fronteira = "ProvasUs" + UUID.randomUUID();
        String corpo = "--" + fronteira + "\r\nContent-Disposition: form-data; name=\"categoria\"\r\n\r\nDEPOSITO\r\n"
                + "--" + fronteira + "\r\nContent-Disposition: form-data; name=\"documentoConfigId\"\r\n\r\n"
                + itemDoProcesso(processo, PROVA_AM) + "\r\n"
                + "--" + fronteira + "\r\nContent-Disposition: form-data; name=\"ficheiro\"; filename=\"provaam.docx\"\r\n"
                + "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n"
                + "docx ficticio\r\n--" + fronteira + "--\r\n";
        mockMvc.perform(post("/api/v1/processos/" + processo + "/documentos").with(camara.jwt())
                        .contentType("multipart/form-data; boundary=" + fronteira)
                        .content(corpo.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("ficheiro")));

        assertThat(contar("SELECT count(*) FROM processo_documento WHERE processo_id = '" + processo + "'")).isZero();
        mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendencias[*].codigo").value(hasItem(PROVA_AM)))
                .andExpect(jsonPath("$.outros[*].nomeFicheiro").value(not(hasItem("provaam.docx"))));
    }

    private void completarSerieMotu(UUID processo, Sessao quem) throws Exception {
        String resposta = mockMvc.perform(get("/api/v1/processos/" + processo + "/metadados-motu").with(quem.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaFichaMotu").value(true))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode registo : objectMapper.readTree(resposta).get("registos")) {
            if (registo.get("opcional").asBoolean() || !"SERIE".equals(registo.get("tipo").asText())) {
                continue;
            }
            mockMvc.perform(put("/api/v1/processos/" + processo + "/metadados-motu/" + registo.get("id").asText())
                            .with(quem.jwt()).contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("tituloAlternativo", "Valor de teste",
                                    "resumo", "Valor de teste", "palavrasChave", "PDM", "denominadorEscala", "10000",
                                    "sistemaReferencia", "PT-TM06/ETRS89", "historico", "Valor de teste",
                                    "relatorio", "Valor de teste"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completo").value(true));
        }
    }

    // --- apoio ---

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private Sessao camaraDeSintra() {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-provas.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", "CM", "nif", nipc,
                "nome", "Entidade de teste " + CODIGO_CAMARA_DE_SINTRA, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade)), utilizadorId);
    }

    /** Instrumento em vigor, Dinâmica e Processo pela API, e o formulário com os elementos afetados indicados. */
    private UUID processoComElementos(String tipoInstrumento, String tipoDinamica, String tipoProcedimento, Sessao quem,
                                      String elementosAfetados) throws Exception {
        Instrumento instrumento = novoInstrumentoEmVigor(tipoInstrumento);
        long dinamicaConfig = dinamicaConfigId(tipoInstrumento, tipoDinamica);
        String resposta = mockMvc.perform(post("/api/v1/instrumentos/" + instrumento.getId() + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoDinamicaConfigId", dinamicaConfig, "descritivo", tipoDinamica + " de teste"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID dinamica = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        long procedimento = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.instrumento_dinamica_config_id = ? "
                + "AND tp.codigo = ?", Long.class, dinamicaConfig, tipoProcedimento);
        resposta = mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(quem.jwt())
                        .contentType("application/json").content(objectMapper.writeValueAsString(
                                Map.of("instrumentoProcedimentoConfigId", procedimento, "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID processo = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(quem.jwt())
                        .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                                + ",\"entidadeEmitente\":\"Câmara Municipal de Sintra\",\"elementosAfetados\":"
                                + elementosAfetados + "}"))
                .andExpect(status().isOk());
        return processo;
    }

    private Instrumento novoInstrumentoEmVigor(String tipo) {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável provas", "responsavel-" + UUID.randomUUID() + "@teste-provas.pt", "INCM"));
        String numero = tipo + "-PROVAS-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> tipo.equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    /** O objeto no storage não interessa à completude: a linha do ficheiro no item basta (provas ou outro item). */
    private void carregarProva(UUID processo, Sessao quem, String codigo) {
        long item = itemDoProcesso(processo, codigo);
        transacao.executeWithoutResult(estado -> {
            DocumentoConfig config = documentoConfigRepository.findById(item).orElseThrow();
            processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processoRepository.findById(processo).orElseThrow(),
                    config, config.getCategoria(), "pdf", codigo.toLowerCase() + ".pdf",
                    "privado/provas/" + UUID.randomUUID(), HASH, "v1", 10,
                    utilizadorRepository.findById(quem.utilizadorId()).orElseThrow()));
        });
    }

    private List<String> pendenciasForaDasProvas(UUID processo, Sessao quem) throws Exception {
        String resposta = mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(quem.jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codigos = new ArrayList<>();
        objectMapper.readTree(resposta).get("pendencias").forEach(item -> codigos.add(item.get("codigo").asText()));
        codigos.removeAll(List.of(PROVA_AM, PROVA_CCDR));
        return codigos;
    }

    private ResultActions submeter(UUID processo, Sessao quem) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processo + "/submeter").with(quem.jwt())
                .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                        + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"));
    }

    private long itemDoProcesso(UUID processo, String codigo) {
        return jdbcTemplate.queryForObject("SELECT d.id FROM documento_config d JOIN processo p "
                + "ON p.instrumento_procedimento_config_id = d.instrumento_procedimento_config_id "
                + "WHERE p.id = ? AND d.codigo = ?", Long.class, processo, codigo);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private long versao(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo);
    }

    private String estado(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT estado FROM processo WHERE id = ?", String.class, processo);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
