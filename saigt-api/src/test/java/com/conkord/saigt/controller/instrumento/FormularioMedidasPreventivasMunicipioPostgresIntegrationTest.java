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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O formulário das Medidas Preventivas do município (Informação Geral, 2.ª série H) contra o PostgreSQL com os seeds
 * reais: a Câmara Municipal de Sintra inicia-as a partir do seu PDM em vigor e também sem IGT, com a configuração do
 * PDM, e cria, grava e submete a Publicação; a Prorrogação usa o mesmo formulário.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class FormularioMedidasPreventivasMunicipioPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String DICO_SINTRA = "1111";
    private static final long ID_INCM_AVISO = 10L;
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

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private record Cenario(UUID processoId, UUID dinamicaId, Sessao camara, String descritivo) {
    }

    // --- Sessões e cenário ---

    private Sessao sessao(String codigoEntidade, String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-mp-municipio.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", "500000000",
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", PART_ID_DA_CAMARA, "letra", "H", "descricao", "Parte H"),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", ID_INCM_AVISO, "nome", "Aviso", "extrato", false))));
        RequestPostProcessor token = jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
        return new Sessao(token, utilizadorId);
    }

    private Sessao camaraDeSintra() {
        return sessao(CODIGO_CAMARA_DE_SINTRA, "CM");
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long dinamicaConfigId() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = 'PDM' AND d.codigo = 'MEDIDAS_PREVENTIVAS'", Long.class);
    }

    private long procedimentoConfigId(String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento p ON p.id = c.tipo_procedimento_id "
                + "WHERE c.instrumento_dinamica_config_id = ? AND p.codigo = ?", Long.class, dinamicaConfigId(), tipoProcedimento);
    }

    /** A Câmara cria o PDM do seu concelho; fica em vigor por SQL (a publicação é de outras stories). */
    private UUID pdmEmVigor(Sessao camara) throws Exception {
        long tipoPdm = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = 'PDM'", Long.class);
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoPdm,
                "designacao", "PDM de Sintra " + UUID.randomUUID(), "concelhos", List.of(DICO_SINTRA)));
        UUID id = UUID.fromString(json(mockMvc.perform(post("/api/v1/instrumentos").with(camara.jwt())
                .contentType("application/json").content(pedido)).andExpect(status().isCreated())).get("id").asText());
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", id);
        return id;
    }

    private static String descritivo() {
        return ("MP Sintra " + UUID.randomUUID()).substring(0, 30);
    }

    private ResultActions pedirProcesso(UUID dinamicaId, Sessao sessao, String tipoProcedimento) throws Exception {
        return mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaId + "/processos").with(sessao.jwt())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "instrumentoProcedimentoConfigId", procedimentoConfigId(tipoProcedimento)))));
    }

    private UUID criarProcesso(UUID dinamicaId, Sessao sessao, String tipoProcedimento) throws Exception {
        return UUID.fromString(json(pedirProcesso(dinamicaId, sessao, tipoProcedimento)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAtoId").value(nullValue()))).get("id").asText());
    }

    /** As Medidas Preventivas iniciadas a partir do PDM de Sintra em vigor, com a Publicação em `EM_SUBMISSAO`. */
    private Cenario comIgt() throws Exception {
        Sessao camara = camaraDeSintra();
        UUID pdm = pdmEmVigor(camara);
        String descritivo = descritivo();
        UUID dinamicaId = UUID.fromString(json(mockMvc.perform(post("/api/v1/instrumentos/" + pdm + "/dinamicas")
                        .with(camara.jwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(),
                                "descritivo", descritivo))))
                .andExpect(status().isCreated())).get("id").asText());
        return new Cenario(criarProcesso(dinamicaId, camara, "PUBLICACAO_MP"), dinamicaId, camara, descritivo);
    }

    /** As Medidas Preventivas iniciadas sem IGT, com a configuração do PDM, e a Publicação em `EM_SUBMISSAO`. */
    private Cenario semIgt() throws Exception {
        Sessao camara = camaraDeSintra();
        String descritivo = descritivo();
        UUID dinamicaId = UUID.fromString(json(mockMvc.perform(post("/api/v1/dinamicas").with(camara.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(),
                                "descritivo", descritivo))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoId").value(nullValue()))
                .andExpect(jsonPath("$.instrumentoIds", empty()))).get("id").asText());
        return new Cenario(criarProcesso(dinamicaId, camara, "PUBLICACAO_MP"), dinamicaId, camara, descritivo);
    }

    private long versao(UUID processoId) {
        return processoRepository.findById(processoId).map(Processo::getVersao).orElseThrow();
    }

    private long tipoAtoDisponivel(Cenario cenario) throws Exception {
        JsonNode tipos = json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.camara().jwt()))
                .andExpect(status().isOk())).path("tiposAtoDisponiveis");
        assertThat(tipos.size()).as("tipos de ato da 2.ª série autorizados à Câmara").isPositive();
        return tipos.get(0).path("id").asLong();
    }

    /** Os campos da secção 1 da story: os do Informação Geral, sem os da Alteração nem a fundamentação RJIGT. */
    private Map<String, Object> formulario(Cenario cenario) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("versao", versao(cenario.processoId()));
        corpo.put("entidadeEmitente", "Câmara Municipal de Sintra");
        corpo.put("contactoTramitacao", "urbanismo@cm-sintra-teste.pt");
        corpo.put("contactoPublicacao", "Divisão de Planeamento, 219 000 000");
        corpo.put("normaLegal", "Artigo 134.º do RJIGT");
        corpo.put("tipoAtoId", tipoAtoDisponivel(cenario));
        corpo.put("tipoInformacao", "TEXTO_INTEGRAL");
        corpo.put("sumario", "Medidas preventivas na área da revisão do PDM de Sintra.");
        corpo.put("dataAssinatura", LocalDate.now().toString());
        corpo.put("elementosAfetados", List.of("PECAS_ESCRITAS"));
        return corpo;
    }

    private ResultActions gravar(Cenario cenario, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processoId() + "/dados-formulario")
                .with(cenario.camara().jwt()).contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    /** Reenvia os campos INCM gravados, como o ecrã. Os itens obrigatórios sem ficheiro levam observações (FR-137). */
    private ResultActions submeter(Cenario cenario) throws Exception {
        jdbcTemplate.update("INSERT INTO processo_documento_observacao (processo_id, documento_config_id, observacoes) "
                + "SELECT p.id, d.id, 'Sem ficheiro no teste.' FROM processo p JOIN documento_config d "
                + "ON d.instrumento_procedimento_config_id = p.instrumento_procedimento_config_id "
                + "WHERE p.id = ? AND d.obrigatorio AND d.ativo "
                + "AND NOT EXISTS (SELECT 1 FROM processo_documento_observacao o "
                + "WHERE o.processo_id = p.id AND o.documento_config_id = d.id)", cenario.processoId());
        Processo gravado = processoRepository.findById(cenario.processoId()).orElseThrow();
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", gravado.getVersao());
        corpo.put("sumario", gravado.getSumario());
        corpo.put("dataAssinatura", gravado.getDataAssinatura() == null ? null : gravado.getDataAssinatura().toString());
        corpo.put("normaLegal", gravado.getNormaLegal());
        corpo.put("tipoInformacao", gravado.getTipoInformacao());
        corpo.put("confirmacoes", PedidosSubmissao.CONFIRMACOES);
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter").with(cenario.camara().jwt())
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    private void verificarRegraDosElementosAfetadosEEnviar(Cenario cenario) throws Exception {
        Map<String, Object> semElementos = formulario(cenario);
        semElementos.put("elementosAfetados", List.of());
        gravar(cenario, semElementos).andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ELEMENTOS_AFETADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        gravar(cenario, formulario(cenario)).andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(processoRepository.findById(cenario.processoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    // --- Critérios ---

    @Test
    void aPublicacaoNumPdmEmVigorTemOFormularioInformacaoGeralNaSegundaSerieHEGravaOsCamposDoFormulario() throws Exception {
        Cenario cenario = comIgt();

        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.camara().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                .andExpect(jsonPath("$.serieDr").value("SEGUNDA_H"))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(true))
                .andExpect(jsonPath("$.instrumentosAbrangidos", hasSize(1)))
                .andExpect(jsonPath("$.territorio.concelhos[0].codigo").value(DICO_SINTRA));

        gravar(cenario, formulario(cenario)).andExpect(status().isOk());
        Processo gravado = processoRepository.findById(cenario.processoId()).orElseThrow();
        assertThat(gravado.getEntidadeEmitente()).isEqualTo("Câmara Municipal de Sintra");
        assertThat(gravado.getContactoTramitacao()).isEqualTo("urbanismo@cm-sintra-teste.pt");
        assertThat(gravado.getContactoPublicacao()).isEqualTo("Divisão de Planeamento, 219 000 000");
        assertThat(gravado.getNormaLegal()).isEqualTo("Artigo 134.º do RJIGT");
        assertThat(gravado.getTipoAto()).isNotNull();
        assertThat(gravado.getTipoInformacao()).isEqualTo("TEXTO_INTEGRAL");
        assertThat(gravado.getSumario()).startsWith("Medidas preventivas");
        assertThat(gravado.getElementosAfetados()).containsExactly("PECAS_ESCRITAS");
    }

    @Test
    void comIgtSemElementosAfetadosOuSemTipoDeAtoRecebe400EComTudoPassaAEnviadoDgt() throws Exception {
        Cenario cenario = comIgt();
        Map<String, Object> semTipoDeAto = formulario(cenario);
        semTipoDeAto.remove("tipoAtoId");
        gravar(cenario, semTipoDeAto).andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(org.hamcrest.Matchers.contains("tipoAtoId")));

        verificarRegraDosElementosAfetadosEEnviar(cenario);
    }

    @Test
    void semIgtCriaSeOProcessoSemTipoDeAtoEODetalheTrazADesignacaoDaDinamicaSemInstrumentosNemTerritorio() throws Exception {
        Cenario cenario = semIgt();

        mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.camara().jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                .andExpect(jsonPath("$.serieDr").value("SEGUNDA_H"))
                .andExpect(jsonPath("$.tipoAtoId").value(nullValue()))
                .andExpect(jsonPath("$.instrumentoId").value(nullValue()))
                .andExpect(jsonPath("$.instrumentosAbrangidos", empty()))
                .andExpect(jsonPath("$.territorio.concelhos", empty()))
                .andExpect(jsonPath("$.territorio.nutsII", empty()))
                .andExpect(jsonPath("$.designacaoInstrumento").value(cenario.descritivo()))
                .andExpect(jsonPath("$.tipoInstrumento").value("PDM"))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(true))
                .andExpect(jsonPath("$.idus").value(nullValue()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica_instrumento WHERE dinamica_id = ?",
                Integer.class, cenario.dinamicaId())).isZero();
    }

    @Test
    void semIgtGravaORascunhoESubmeteComAMesmaRegraDosElementosAfetadosDoProcessoComIgt() throws Exception {
        Cenario cenario = semIgt();
        Map<String, Object> rascunho = new LinkedHashMap<>();
        rascunho.put("versao", versao(cenario.processoId()));
        rascunho.put("sumario", "Rascunho das medidas preventivas.");
        gravar(cenario, rascunho).andExpect(status().isOk());
        gravar(cenario, formulario(cenario)).andExpect(status().isOk());

        verificarRegraDosElementosAfetadosEEnviar(cenario);
    }

    @Test
    void umaCcdrSemPermissaoNoProcedimentoRecebe403AoCriarOProcesso() throws Exception {
        Cenario cenario = semIgt();
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR");
        long antes = jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE dinamica_id = ?", Long.class,
                cenario.dinamicaId());

        pedirProcesso(cenario.dinamicaId(), ccdr, "PRORROGACAO_MP")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE dinamica_id = ?", Long.class,
                cenario.dinamicaId())).isEqualTo(antes);
    }

    @Test
    void semIgtAEstruturaDocumentalTemOsMesmosItensDoProcessoComIgtDoPdm() throws Exception {
        long publicacaoMpDoPdm = procedimentoConfigId("PUBLICACAO_MP");
        // Itens de prova, não obrigatórios, para a comparação não depender do seed dos itens das Medidas Preventivas.
        jdbcTemplate.update("INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, nome, separador, "
                + "categoria, tipo_elemento, formato, extensoes_aceites, ordem) "
                + "SELECT ?, v.codigo, 'Item de prova', 'Prova', v.categoria, 'PECA_ESCRITA', v.formato, '[\"pdf\"]', v.ordem "
                + "FROM (VALUES ('PROVA_MP_PUB', 'PUBLICACAO', 'EDITAVEL', 901), ('PROVA_MP_DEP', 'DEPOSITO', 'NAO_EDITAVEL', 902)) "
                + "AS v (codigo, categoria, formato, ordem) "
                + "WHERE NOT EXISTS (SELECT 1 FROM documento_config d WHERE d.instrumento_procedimento_config_id = ? "
                + "AND d.codigo = v.codigo)", publicacaoMpDoPdm, publicacaoMpDoPdm);
        Cenario comIgt = comIgt();
        Cenario semIgt = semIgt();
        gravar(comIgt, formulario(comIgt)).andExpect(status().isOk());
        gravar(semIgt, formulario(semIgt)).andExpect(status().isOk());

        JsonNode estruturaComIgt = estrutura(comIgt);
        JsonNode estruturaSemIgt = estrutura(semIgt);
        assertThat(estruturaSemIgt.path("separadores")).isEqualTo(estruturaComIgt.path("separadores"));
        assertThat(estruturaSemIgt.path("pendencias")).isEqualTo(estruturaComIgt.path("pendencias"));
        assertThat(estruturaSemIgt.findValuesAsText("codigo")).contains("PROVA_MP_PUB", "PROVA_MP_DEP");
    }

    private JsonNode estrutura(Cenario cenario) throws Exception {
        return json(mockMvc.perform(get("/api/v1/processos/" + cenario.processoId() + "/estrutura-documental")
                .with(cenario.camara().jwt())).andExpect(status().isOk()));
    }

    @Test
    void aProrrogacaoDasMedidasPreventivasComESemIgtTemOFormularioInformacaoGeral() throws Exception {
        for (Cenario publicacao : List.of(comIgt(), semIgt())) {
            jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '215', "
                    + "data_publicacao_dr = DATE '2026-11-05' WHERE id = ?", publicacao.processoId());
            UUID prorrogacao = criarProcesso(publicacao.dinamicaId(), publicacao.camara(), "PRORROGACAO_MP");

            mockMvc.perform(get("/api/v1/processos/" + prorrogacao).with(publicacao.camara().jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                    .andExpect(jsonPath("$.serieDr").value("SEGUNDA_H"));
        }
    }
}
