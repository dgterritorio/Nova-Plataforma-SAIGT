package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
 * Os formulários da Suspensão e das Medidas Preventivas do Governo contra o PostgreSQL com os seeds reais do Liquibase:
 * a Secretaria de Estado (entidade 3001) inicia a Dinâmica sem IGT, cria o processo da Publicação, associa os PDM de
 * Sintra e de Cascais (criados pelas Câmaras e postos em vigor aqui) e, depois de a Publicação ser publicada, a
 * Prorrogação.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class FormularioGovernoPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CAMARA_DE_CASCAIS = "5085";
    private static final String CODIGO_SEC_ESTADO = "3001";
    private static final long ID_INCM_AVISO = 10L;
    private static final long PART_ID_DA_CAMARA = 7_000_000_000_000_001L;
    private static final String NORMA_LEGAL = "Artigo 126.º do RJIGT";

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
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private TransactionTemplate transacao;

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId, Long entidadeId) {
    }

    private record Cenario(UUID processoId, UUID dinamicaId, Sessao governo) {
    }

    // --- Sessões e cenário ---

    private long tipoAtoPrimeiraSerieId() {
        return jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato WHERE incm_act_type_id_serie_1 IS NOT NULL", Long.class);
    }

    private long idIncmDoTipoAto() {
        return jdbcTemplate.queryForObject("SELECT incm_act_type_id_serie_1 FROM tipo_ato WHERE id = ?", Long.class,
                tipoAtoPrimeiraSerieId());
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, Map<String, Object> atosAutorizados) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-governo.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", "500000000",
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", PART_ID_DA_CAMARA, "letra", "H", "descricao", "Parte H"),
                "atos_autorizados", atosAutorizados);
        RequestPostProcessor token = jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
        return new Sessao(token, utilizadorId, entidadeId);
    }

    private Sessao secretariaDeEstado() {
        return sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", Map.of("serie_1", List.of(idIncmDoTipoAto()), "serie_2", List.of()));
    }

    private Sessao camara(String codigo) {
        return sessao(codigo, "CM", Map.of("serie_1", List.of(),
                "serie_2", List.of(Map.of("id", ID_INCM_AVISO, "nome", "Aviso", "extrato", false))));
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long dinamicaConfigId(String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = ?", Long.class, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoDinamica, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento p ON p.id = c.tipo_procedimento_id "
                + "WHERE c.instrumento_dinamica_config_id = ? AND p.codigo = ?", Long.class,
                dinamicaConfigId(tipoDinamica), tipoProcedimento);
    }

    /** A Câmara cria o PDM do seu concelho; fica em vigor por SQL (a publicação é de outras stories). */
    private UUID pdmEmVigor(Sessao camara, String designacao, String dico) throws Exception {
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PDM"),
                "designacao", designacao + " " + UUID.randomUUID(), "concelhos", List.of(dico)));
        UUID id = UUID.fromString(json(mockMvc.perform(post("/api/v1/instrumentos").with(camara.jwt())
                .contentType("application/json").content(pedido)).andExpect(status().isCreated())).get("id").asText());
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", id);
        return id;
    }

    private UUID criarProcesso(UUID dinamicaId, Sessao governo, String tipoDinamica, String tipoProcedimento) throws Exception {
        String processo = objectMapper.writeValueAsString(Map.of(
                "instrumentoProcedimentoConfigId", procedimentoConfigId(tipoDinamica, tipoProcedimento)));
        return UUID.fromString(json(mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaId + "/processos")
                .with(governo.jwt()).contentType("application/json").content(processo))
                .andExpect(status().isCreated())).get("id").asText());
    }

    /** A Publicação da Dinâmica do Governo sobre os PDM de Sintra e de Cascais, em `EM_SUBMISSAO`. */
    private Cenario publicacao(String tipoDinamica, String tipoProcedimento) throws Exception {
        UUID pdmSintra = pdmEmVigor(camara(CODIGO_CAMARA_DE_SINTRA), "PDM de Sintra", "1111");
        UUID pdmCascais = pdmEmVigor(camara(CODIGO_CAMARA_DE_CASCAIS), "PDM de Cascais", "1105");
        Sessao governo = secretariaDeEstado();

        String inicio = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoDinamica),
                "descritivo", ("Governo " + UUID.randomUUID()).substring(0, 40)));
        UUID dinamicaId = UUID.fromString(json(mockMvc.perform(post("/api/v1/dinamicas").with(governo.jwt())
                .contentType("application/json").content(inicio)).andExpect(status().isCreated())).get("id").asText());
        UUID processoId = criarProcesso(dinamicaId, governo, tipoDinamica, tipoProcedimento);
        mockMvc.perform(put("/api/v1/dinamicas/" + dinamicaId + "/instrumentos").with(governo.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoIds", List.of(pdmSintra, pdmCascais)))))
                .andExpect(status().isOk());
        return new Cenario(processoId, dinamicaId, governo);
    }

    /** A Publicação publicada por SQL (a INCM é de outras stories) e a Prorrogação criada na mesma Dinâmica. */
    private Cenario prorrogacao(String tipoDinamica, String publicacao, String prorrogacao) throws Exception {
        Cenario cenario = publicacao(tipoDinamica, publicacao);
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '215', "
                + "data_publicacao_dr = DATE '2026-11-05' WHERE id = ?", cenario.processoId());
        UUID processoId = criarProcesso(cenario.dinamicaId(), cenario.governo(), tipoDinamica, prorrogacao);
        return new Cenario(processoId, cenario.dinamicaId(), cenario.governo());
    }

    private long versao(UUID processoId) {
        return processoRepository.findById(processoId).map(Processo::getVersao).orElseThrow();
    }

    private Map<String, Object> formulario(UUID processoId) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("versao", versao(processoId));
        corpo.put("sumario", "Suspensão parcial dos PDM de Sintra e de Cascais.");
        corpo.put("dataAssinatura", LocalDate.now().toString());
        corpo.put("normaLegal", NORMA_LEGAL);
        corpo.put("tipoInformacao", "TEXTO_INTEGRAL");
        corpo.put("publicarSuplemento", false);
        corpo.put("entidadeEmitente", "Secretaria de Estado do Ordenamento do Território");
        corpo.put("contactoTramitacao", "tramitacao@governo-teste.pt");
        corpo.put("contactoPublicacao", "Gabinete, 210 000 000");
        corpo.put("tipoAtoId", tipoAtoPrimeiraSerieId());
        corpo.put("elementosAfetados", List.of());
        return corpo;
    }

    private ResultActions gravar(Cenario cenario, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processoId() + "/dados-formulario")
                .with(cenario.governo().jwt()).contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    /** Reenvia os campos INCM gravados, como o ecrã. */
    private ResultActions submeter(Cenario cenario) throws Exception {
        Processo gravado = processoRepository.findById(cenario.processoId()).orElseThrow();
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", gravado.getVersao());
        corpo.put("sumario", gravado.getSumario());
        corpo.put("dataAssinatura", gravado.getDataAssinatura() == null ? null : gravado.getDataAssinatura().toString());
        corpo.put("normaLegal", gravado.getNormaLegal());
        corpo.put("tipoInformacao", gravado.getTipoInformacao());
        corpo.put("publicarSuplemento", gravado.getPublicarSuplemento());
        corpo.put("dataSuplemento", gravado.getDataSuplemento() == null ? null : gravado.getDataSuplemento().toString());
        corpo.put("confirmacoes", PedidosSubmissao.CONFIRMACOES);
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter").with(cenario.governo().jwt())
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    private void carregar(Cenario cenario, String codigo, String extensao) {
        transacao.executeWithoutResult(estado -> {
            Processo processo = processoRepository.findById(cenario.processoId()).orElseThrow();
            Long itemId = jdbcTemplate.queryForObject("SELECT id FROM documento_config WHERE codigo = ? "
                    + "AND instrumento_procedimento_config_id = ?", Long.class, codigo,
                    processo.getInstrumentoProcedimentoConfig().getId());
            String nome = codigo.toLowerCase() + "." + extensao;
            CategoriaDocumento categoria = codigo.endsWith("_DEP") ? CategoriaDocumento.DEPOSITO : CategoriaDocumento.PUBLICACAO;
            processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo,
                    documentoConfigRepository.findById(itemId).orElseThrow(), categoria, extensao,
                    nome, "processos/teste/" + nome, "a".repeat(64), "1", 10L,
                    utilizadorRepository.findById(cenario.governo().utilizadorId()).orElseThrow()));
        });
    }

    /** Os seis itens da lista da DGT na Suspensão do Governo do PDM, todos obrigatórios nas duas caixas (FR-193, OI-548). */
    private void carregarObrigatoriosDaSuspensao(Cenario cenario) {
        for (String codigo : List.of("RCM_PUB", "RCM_DEP", "TEXTO_MEDIDAS_PREVENTIVAS_PUB", "TEXTO_MEDIDAS_PREVENTIVAS_DEP")) {
            carregar(cenario, codigo, "pdf");
        }
        carregar(cenario, "PLANTA_DELIMITACAO_PUB", "zip");
        carregar(cenario, "PLANTA_DELIMITACAO_DEP", "zip");
    }

    /** A planta de publicação faz do Procedimento final um com ficha MOTU: a Série completa-se pela API. */
    private void completarFichaMotu(Cenario cenario) throws Exception {
        JsonNode motu = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId() + "/metadados-motu")
                .with(cenario.governo().jwt())).andExpect(status().isOk()));
        if (!motu.path("aplicaFichaMotu").asBoolean()) {
            return;
        }
        for (Iterator<JsonNode> registos = motu.path("registos").elements(); registos.hasNext(); ) {
            JsonNode registo = registos.next();
            if (!"SERIE".equals(registo.path("tipo").asText())) {
                continue;
            }
            Map<String, Object> serie = Map.of("tituloAlternativo", "Valor de teste", "resumo", "Valor de teste",
                    "palavrasChave", "PDM", "denominadorEscala", "10000", "sistemaReferencia", "PT-TM06/ETRS89",
                    "historico", "Valor de teste", "relatorio", "Valor de teste");
            mockMvc.perform(put("/api/v1/processos/" + cenario.processoId() + "/metadados-motu/" + registo.path("id").asText())
                            .with(cenario.governo().jwt()).contentType("application/json")
                            .content(objectMapper.writeValueAsString(serie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completo").value(true));
        }
    }

    private Map<String, Boolean> itensDePublicacao(Cenario cenario) throws Exception {
        JsonNode estrutura = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId() + "/estrutura-documental")
                .with(cenario.governo().jwt())).andExpect(status().isOk()));
        Map<String, Boolean> publicacao = new LinkedHashMap<>();
        estrutura.path("separadores").forEach(separador -> separador.path("publicacao")
                .forEach(item -> publicacao.put(item.path("nome").asText(), item.path("obrigatorio").asBoolean())));
        return publicacao;
    }

    // --- Critérios ---

    @Test
    void aPublicacaoDaSuspensaoTemOFormularioSuspensaoMpGovernoEAProrrogacaoDasMpOProrrogacaoGoverno() throws Exception {
        Cenario suspensao = publicacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO");
        mockMvc.perform(get("/api/v1/processos/" + suspensao.processoId()).with(suspensao.governo().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.formulario").value("SUSPENSAO_MP_GOVERNO"))
                .andExpect(jsonPath("$.serieDr").value("PRIMEIRA"))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(false))
                .andExpect(jsonPath("$.designacaoInstrumento").value(org.hamcrest.Matchers.startsWith("Governo ")))
                .andExpect(jsonPath("$.instrumentosAbrangidos", hasSize(2)));

        Cenario medidas = prorrogacao("MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO", "PRORROGACAO_MP_GOVERNO");
        mockMvc.perform(get("/api/v1/processos/" + medidas.processoId()).with(medidas.governo().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("PRORROGACAO_GOVERNO"))
                .andExpect(jsonPath("$.serieDr").value("PRIMEIRA"))
                .andExpect(jsonPath("$.instrumentosAbrangidos", hasSize(2)));
    }

    @Test
    void semOTipoDeInformacaoASubmissaoRecebe400NoCampoEComTudoEOsDocumentosObrigatoriosPassaAEnviadoDgt() throws Exception {
        Cenario cenario = publicacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO");
        Map<String, Object> semTipoInformacao = formulario(cenario.processoId());
        semTipoInformacao.put("tipoInformacao", null);
        gravar(cenario, semTipoInformacao).andExpect(status().isOk());
        carregarObrigatoriosDaSuspensao(cenario);
        completarFichaMotu(cenario);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique o tipo de informação."));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        gravar(cenario, formulario(cenario.processoId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.governo().jwt()))
                .andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        Processo submetido = processoRepository.findById(cenario.processoId()).orElseThrow();
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(submetido.getPublicarSuplemento()).isFalse();
        assertThat(submetido.getElementosAfetados()).isNullOrEmpty();
    }

    @Test
    void aSubmissaoDaPublicacaoExigeOsCamposDoFormularioNumaSoRecusa() throws Exception {
        // As MP do Governo dos planos não têm itens: a lista da DGT prevalece (FR-193).
        Cenario cenario = publicacao("MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO");

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter").with(cenario.governo().jwt())
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(cenario.processoId()) + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder("entidadeEmitente", "contactoTramitacao",
                        "contactoPublicacao", "normaLegal", "tipoAtoId", "publicarSuplemento", "tipoInformacao", "sumario",
                        "dataAssinatura")))
                .andExpect(jsonPath("$.campos[?(@.path == 'entidadeEmitente')].erro")
                        .value(hasItem("Indique a identificação da entidade emitente.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'contactoTramitacao')].erro")
                        .value(hasItem("Indique um e-mail válido para a tramitação.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'contactoPublicacao')].erro")
                        .value(hasItem("Indique o contacto para publicação.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'normaLegal')].erro")
                        .value(hasItem("Indique a norma legal ou mera conveniência.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'tipoAtoId')].erro").value(hasItem("Indique o tipo de ato.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'publicarSuplemento')].erro")
                        .value(hasItem("Indique se a publicação é no Diário ou em Suplemento.")))
                .andExpect(jsonPath("$.campos[?(@.path == 'sumario')].erro").value(hasItem("Indique o sumário do ato.")));
    }

    @Test
    void comSuplementoGravaPublicarSuplementoEAProrrogacaoSemPublicacaoEAceiteEGravaDiario() throws Exception {
        Cenario suspensao = publicacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO");
        Map<String, Object> suplemento = formulario(suspensao.processoId());
        suplemento.put("publicarSuplemento", true);
        gravar(suspensao, suplemento).andExpect(status().isOk());
        carregarObrigatoriosDaSuspensao(suspensao);
        completarFichaMotu(suspensao);

        submeter(suspensao)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("dataSuplemento"));
        suplemento = formulario(suspensao.processoId());
        suplemento.put("publicarSuplemento", true);
        suplemento.put("dataSuplemento", LocalDate.now().plusDays(3).toString());
        gravar(suspensao, suplemento).andExpect(status().isOk());
        submeter(suspensao).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(processoRepository.findById(suspensao.processoId()).orElseThrow().getPublicarSuplemento()).isTrue();

        Cenario prorrogacao = prorrogacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", "PRORROGACAO_SUSPENSAO_GOVERNO");
        Map<String, Object> semPublicacao = formulario(prorrogacao.processoId());
        semPublicacao.remove("publicarSuplemento");
        semPublicacao.put("dataSuplemento", LocalDate.now().toString());
        gravar(prorrogacao, semPublicacao).andExpect(status().isOk());
        Processo gravada = processoRepository.findById(prorrogacao.processoId()).orElseThrow();
        assertThat(gravada.getPublicarSuplemento()).isFalse();
        assertThat(gravada.getDataSuplemento()).isNull();
        carregar(prorrogacao, "RCM_PUB", "pdf");
        carregar(prorrogacao, "RCM_DEP", "pdf");
        completarFichaMotu(prorrogacao);
        assertThat(itensDePublicacao(prorrogacao)).containsExactlyEntriesOf(Map.of("Resolução do Conselho de Ministros", true));

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao(prorrogacao.processoId()));
        corpo.put("sumario", "Prorrogação da suspensão.");
        corpo.put("dataAssinatura", LocalDate.now().toString());
        corpo.put("normaLegal", NORMA_LEGAL);
        corpo.put("tipoInformacao", "EXTRATO");
        corpo.put("publicarSuplemento", true);
        corpo.put("confirmacoes", PedidosSubmissao.CONFIRMACOES);
        mockMvc.perform(patch("/api/v1/processos/" + prorrogacao.processoId() + "/submeter").with(prorrogacao.governo().jwt())
                        .contentType("application/json").content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        Processo submetida = processoRepository.findById(prorrogacao.processoId()).orElseThrow();
        assertThat(submetida.getPublicarSuplemento()).isFalse();
        assertThat(submetida.getDataSuplemento()).isNull();
    }

    @Test
    void umContactoDeTramitacaoSemDominioGravaNoRascunhoESubmeterRecebe400NoCampo() throws Exception {
        Cenario cenario = publicacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO");
        Map<String, Object> corpo = formulario(cenario.processoId());
        corpo.put("contactoTramitacao", "tramitacao@gov");
        gravar(cenario, corpo).andExpect(status().isOk());
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getContactoTramitacao())
                .isEqualTo("tramitacao@gov");
        carregarObrigatoriosDaSuspensao(cenario);
        completarFichaMotu(cenario);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("contactoTramitacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique um e-mail válido para a tramitação."));
        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.governo().jwt()))
                .andExpect(jsonPath("$.podeSubmeter").value(false));
    }

    @Test
    void osItensDocumentaisDaSuspensaoEDasMedidasPreventivasDoGovernoNoPdmSaoOsDaListaDaDgt() throws Exception {
        // A lista da DGT prevalece sobre a EF v1.5 (FR-193): três elementos na Suspensão e nenhum nas MP do Governo.
        Cenario suspensao = publicacao("SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO");
        Map<String, Boolean> esperadosSuspensao = new LinkedHashMap<>();
        esperadosSuspensao.put("Resolução do Conselho de Ministros", true);
        esperadosSuspensao.put("Texto das Medidas Preventivas", true);
        esperadosSuspensao.put("Planta de delimitação da área abrangida pela suspensão - quando aplicável", true);
        assertThat(itensDePublicacao(suspensao)).containsExactlyInAnyOrderEntriesOf(esperadosSuspensao);

        Cenario medidas = publicacao("MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO");
        assertThat(itensDePublicacao(medidas)).isEmpty();
    }

    @Test
    void depoisDaSubmissaoOsCamposFicamBloqueadosEAProrrogacaoReabertaGravaSemAPublicacao() throws Exception {
        Cenario cenario = prorrogacao("MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO", "PRORROGACAO_MP_GOVERNO");
        gravar(cenario, formulario(cenario.processoId())).andExpect(status().isOk());
        // Reaberto pelo autor depois de uma rejeição: já submetido uma vez, de novo em edição.
        jdbcTemplate.update("UPDATE processo SET data_submissao = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()),
                cenario.processoId());

        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.governo().jwt()))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem("sumario")))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem("publicarSuplemento")))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem("tipoAtoId")));

        Map<String, Object> outroSumario = formulario(cenario.processoId());
        outroSumario.put("sumario", "Outro sumário.");
        outroSumario.put("publicarSuplemento", true);
        gravar(cenario, outroSumario)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("sumario")))
                .andExpect(jsonPath("$.campos[*].path").value(not(hasItem("publicarSuplemento"))));

        Map<String, Object> igual = formulario(cenario.processoId());
        igual.remove("publicarSuplemento");
        gravar(cenario, igual).andExpect(status().isOk());
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getPublicarSuplemento()).isFalse();
    }
}
