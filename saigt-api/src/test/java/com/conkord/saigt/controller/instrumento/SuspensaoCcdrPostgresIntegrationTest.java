package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
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
 * O formulário da Suspensão da iniciativa da CCDR contra o PostgreSQL com os seeds reais do Liquibase: a CCDR LVT
 * (entidade 8001) inicia a Dinâmica sem IGT, associa os PDM de Sintra e de Cascais (criados pelas Câmaras e postos em
 * vigor aqui) e cria o processo do Procedimento `SUSPENSAO_CCDR`, 2.ª série parte C, com o Despacho.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SuspensaoCcdrPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CAMARA_DE_CASCAIS = "5085";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String NOME_CCDR_LVT = "Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP";
    private static final String NOME_CCDR_NORTE = "Comissão de Coordenação e Desenvolvimento Regional do Norte, IP";
    private static final String MSG_ENTIDADE_EMITENTE = "Escolha a entidade emitente da lista das CCDR.";
    private static final long ID_INCM_DESPACHO = 11L;
    private static final long ID_INCM_AVISO = 10L;
    private static final long PART_ID_DA_CCDR = 7_000_000_000_000_008L;
    private static final long PART_ID_DA_CAMARA = 7_000_000_000_000_001L;

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

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId, Long entidadeId) {
    }

    private record Cenario(UUID processoId, UUID dinamicaId, Sessao ccdr, Sessao camaraDeSintra) {
    }

    // --- Sessões e cenário ---

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte, long partId, long atoSerie2) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-suspensao.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", "500000000",
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", partId, "letra", letraParte, "descricao", "Parte " + letraParte),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", atoSerie2, "nome", "Ato", "extrato", false))));
        RequestPostProcessor token = jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
        return new Sessao(token, utilizadorId, entidadeId);
    }

    private Sessao ccdrLvt() {
        return sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C", PART_ID_DA_CCDR, ID_INCM_DESPACHO);
    }

    private Sessao camara(String codigo) {
        return sessao(codigo, "CM", "H", PART_ID_DA_CAMARA, ID_INCM_AVISO);
    }

    private static RequestPostProcessor jwtMaster() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "MASTER"));
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long dinamicaConfigId() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = 'SUSPENSAO_CCDR'", Long.class);
    }

    private long procedimentoConfigId() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento p ON p.id = c.tipo_procedimento_id "
                + "WHERE c.instrumento_dinamica_config_id = ? AND p.codigo = 'SUSPENSAO_CCDR'", Long.class, dinamicaConfigId());
    }

    private long tipoAtoDespachoId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class, ID_INCM_DESPACHO);
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

    private Cenario suspensaoSobreSintraECascais() throws Exception {
        Sessao camaraDeSintra = camara(CODIGO_CAMARA_DE_SINTRA);
        UUID pdmSintra = pdmEmVigor(camaraDeSintra, "PDM de Sintra", "1111");
        UUID pdmCascais = pdmEmVigor(camara(CODIGO_CAMARA_DE_CASCAIS), "PDM de Cascais", "1105");
        Sessao ccdr = ccdrLvt();

        String inicio = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(),
                "descritivo", ("Suspensão " + UUID.randomUUID()).substring(0, 40)));
        UUID dinamicaId = UUID.fromString(json(mockMvc.perform(post("/api/v1/dinamicas").with(ccdr.jwt())
                .contentType("application/json").content(inicio)).andExpect(status().isCreated())).get("id").asText());
        String processo = objectMapper.writeValueAsString(Map.of("instrumentoProcedimentoConfigId", procedimentoConfigId(),
                "tipoAtoId", tipoAtoDespachoId()));
        UUID processoId = UUID.fromString(json(mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaId + "/processos")
                .with(ccdr.jwt()).contentType("application/json").content(processo))
                .andExpect(status().isCreated())).get("id").asText());
        // Só o autor de um processo da Dinâmica em submissão associa os planos.
        mockMvc.perform(put("/api/v1/dinamicas/" + dinamicaId + "/instrumentos").with(ccdr.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoIds", List.of(pdmSintra, pdmCascais)))))
                .andExpect(status().isOk());
        return new Cenario(processoId, dinamicaId, ccdr, camaraDeSintra);
    }

    private long versao(UUID processoId) {
        return processoRepository.findById(processoId).map(Processo::getVersao).orElseThrow();
    }

    private Map<String, Object> formulario(UUID processoId, String entidadeEmitente, String normaLegal) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao(processoId));
        corpo.put("sumario", "Suspensão parcial dos PDM de Sintra e de Cascais.");
        corpo.put("dataAssinatura", LocalDate.now().toString());
        corpo.put("normaLegal", normaLegal);
        corpo.put("tipoInformacao", "TEXTO_INTEGRAL");
        corpo.put("entidadeEmitente", entidadeEmitente);
        corpo.put("contactoTramitacao", "tramitacao@ccdr-teste.pt");
        corpo.put("contactoPublicacao", "Divisão de Ordenamento, 210 000 000");
        corpo.put("elementosAfetados", List.of());
        return corpo;
    }

    private ResultActions gravar(Cenario cenario, Map<String, Object> corpo, RequestPostProcessor quem) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processoId() + "/dados-formulario").with(quem)
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions submeter(Cenario cenario, RequestPostProcessor quem) throws Exception {
        Map<String, Object> corpo = new HashMap<>(formulario(cenario.processoId(), null, null));
        Processo gravado = processoRepository.findById(cenario.processoId()).orElseThrow();
        corpo.put("normaLegal", gravado.getNormaLegal());
        corpo.remove("entidadeEmitente");
        corpo.remove("contactoTramitacao");
        corpo.remove("contactoPublicacao");
        corpo.remove("elementosAfetados");
        corpo.put("confirmacoes", PedidosSubmissao.CONFIRMACOES);
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter").with(quem)
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    /** Com uma planta de publicação o Procedimento final teria ficha MOTU: a Série completa-se pela API. */
    private void completarFichaMotu(Cenario cenario) throws Exception {
        JsonNode motu = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId() + "/metadados-motu")
                .with(cenario.ccdr().jwt())).andExpect(status().isOk()));
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
                            .with(cenario.ccdr().jwt()).contentType("application/json")
                            .content(objectMapper.writeValueAsString(serie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completo").value(true));
        }
    }

    // --- Critérios ---

    @Test
    void aListaDasEmitentesCcdrTemSoIdENomeDasAtivasPorOrdemESemTokenDa401() throws Exception {
        jdbcTemplate.update("UPDATE entidade SET ativo = false WHERE codigo = '8007'");
        try {
            JsonNode lista = json(mockMvc.perform(get("/api/v1/entidades/emitentes").param("tipoEntidade", "CCDR_DR")
                    .with(ccdrLvt().jwt())).andExpect(status().isOk()));

            assertThat(lista).hasSize(6);
            List<String> nomes = lista.findValuesAsText("nome");
            assertThat(nomes).contains(NOME_CCDR_LVT, NOME_CCDR_NORTE).isSorted()
                    .doesNotContain("Secretaria Regional de Agricultura e Pescas");
            lista.forEach(entidade -> assertThat(entidade.fieldNames()).toIterable().containsExactly("id", "nome"));
        } finally {
            jdbcTemplate.update("UPDATE entidade SET ativo = true WHERE codigo = '8007'");
        }

        mockMvc.perform(get("/api/v1/entidades/emitentes").param("tipoEntidade", "CCDR_DR"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/entidades/emitentes").param("tipoEntidade", "CCDR_DR").with(jwtMaster()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/entidades/emitentes").param("tipoEntidade", "CCDR").with(ccdrLvt().jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoEntidade"));
        mockMvc.perform(get("/api/v1/entidades/emitentes").with(ccdrLvt().jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oDetalheDaSuspensaoTemOFormularioDaSegundaSerieCEOsDoisPdm() throws Exception {
        Cenario cenario = suspensaoSobreSintraECascais();

        JsonNode detalhe = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.ccdr().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("SUSPENSAO_CCDR"))
                .andExpect(jsonPath("$.serieDr").value("SEGUNDA_C"))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(false))
                .andExpect(jsonPath("$.instrumentosAbrangidos", hasSize(2))));
        assertThat(detalhe.path("instrumentosAbrangidos").findValuesAsText("designacao"))
                .anySatisfy(designacao -> assertThat(designacao).startsWith("PDM de Sintra"))
                .anySatisfy(designacao -> assertThat(designacao).startsWith("PDM de Cascais"));
    }

    @Test
    void umaEntidadeEmitenteForaDaListaDasCcdrNaoSeGravaEUmaDaListaGrava() throws Exception {
        Cenario cenario = suspensaoSobreSintraECascais();

        gravar(cenario, formulario(cenario.processoId(), "Câmara Municipal de Sintra", "Artigo 126.º do RJIGT"),
                cenario.ccdr().jwt())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(MSG_ENTIDADE_EMITENTE))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_ENTIDADE_EMITENTE));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEntidadeEmitente()).isNull();

        gravar(cenario, formulario(cenario.processoId(), null, null), cenario.ccdr().jwt()).andExpect(status().isOk());
        gravar(cenario, formulario(cenario.processoId(), NOME_CCDR_NORTE, null), cenario.ccdr().jwt())
                .andExpect(status().isOk());
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEntidadeEmitente())
                .isEqualTo(NOME_CCDR_NORTE);
    }

    @Test
    void semANormaLegalASubmissaoRecebe400NoCampoEComTudoPassaAEnviadoDgt() throws Exception {
        Cenario cenario = suspensaoSobreSintraECascais();
        gravar(cenario, formulario(cenario.processoId(), NOME_CCDR_LVT, null), cenario.ccdr().jwt()).andExpect(status().isOk());
        completarFichaMotu(cenario);

        submeter(cenario, cenario.ccdr().jwt())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("normaLegal"));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        gravar(cenario, formulario(cenario.processoId(), NOME_CCDR_LVT, "Artigo 126.º do RJIGT"), cenario.ccdr().jwt())
                .andExpect(status().isOk());
        submeter(cenario, cenario.ccdr().jwt())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        Processo submetido = processoRepository.findById(cenario.processoId()).orElseThrow();
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(submetido.getDrParteId()).isEqualTo(PART_ID_DA_CCDR);
        assertThat(submetido.getEntidadeEmitente()).isEqualTo(NOME_CCDR_LVT);

        // Critério 7: a Câmara de Sintra não vê o processo da CCDR.
        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.camaraDeSintra().jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSubmissaoExigeOsCamposDoFormularioNumaSoRecusaEUmaEmitenteQueDeixouDeEstarAtivaNaoPassa() throws Exception {
        Cenario cenario = suspensaoSobreSintraECascais();

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter").with(cenario.ccdr().jwt())
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(cenario.processoId()) + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder("entidadeEmitente", "contactoTramitacao",
                        "contactoPublicacao", "normaLegal", "tipoInformacao", "sumario", "dataAssinatura")))
                .andExpect(jsonPath("$.campos[?(@.path == 'entidadeEmitente')].erro").value(hasItem(MSG_ENTIDADE_EMITENTE)));

        gravar(cenario, formulario(cenario.processoId(), NOME_CCDR_NORTE, "Artigo 126.º do RJIGT"), cenario.ccdr().jwt())
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE entidade SET ativo = false WHERE codigo = '8005'");
        try {
            submeter(cenario, cenario.ccdr().jwt())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"));
        } finally {
            jdbcTemplate.update("UPDATE entidade SET ativo = true WHERE codigo = '8005'");
        }
    }

    @Test
    void umTipoDeAtoForaDosAtosAutorizadosDaAdesaoDaCcdrRecebe403NaSubmissao() throws Exception {
        Cenario cenario = suspensaoSobreSintraECascais();
        gravar(cenario, formulario(cenario.processoId(), NOME_CCDR_LVT, "Artigo 126.º do RJIGT"), cenario.ccdr().jwt())
                .andExpect(status().isOk());
        completarFichaMotu(cenario);
        Sessao semDespacho = new Sessao(jwt().jwt(builder -> builder.claim(SUB, cenario.ccdr().utilizadorId().toString())
                .claim("origem", "INCM").claim("entidade", Map.of("id", cenario.ccdr().entidadeId(), "tipo_entidade", "CCDR_DR",
                        "nif", "500000000", "nome", "CCDR LVT", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", PART_ID_DA_CCDR, "letra", "C", "descricao", "Parte C"),
                        "atos_autorizados", Map.of("serie_1", List.of(),
                                "serie_2", List.of(Map.of("id", ID_INCM_AVISO, "nome", "Aviso", "extrato", true)))))),
                cenario.ccdr().utilizadorId(), cenario.ccdr().entidadeId());

        submeter(cenario, semDespacho.jwt())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TIPO_ATO_NAO_AUTORIZADO"));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void aEstruturaDocumentalNaoTemItensPorqueAListaDaDgtNaoTemASuspensaoDaCcdr() throws Exception {
        // A lista da DGT prevalece sobre a EF v1.5 (FR-193): a Suspensão da CCDR dos planos fica sem itens.
        Cenario cenario = suspensaoSobreSintraECascais();

        JsonNode estrutura = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId() + "/estrutura-documental")
                .with(cenario.ccdr().jwt())).andExpect(status().isOk()));
        Map<String, Boolean> publicacao = new HashMap<>();
        estrutura.path("separadores").forEach(separador -> separador.path("publicacao")
                .forEach(item -> publicacao.put(item.path("nome").asText(), item.path("obrigatorio").asBoolean())));
        assertThat(publicacao).isEmpty();
        assertThat(estrutura.path("pendencias").size()).isZero();
    }
}
