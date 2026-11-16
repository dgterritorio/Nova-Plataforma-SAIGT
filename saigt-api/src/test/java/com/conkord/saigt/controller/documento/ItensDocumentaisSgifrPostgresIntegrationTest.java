package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.exception.DocumentosObrigatoriosEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os itens documentais do SGIFR semeados pelo Liquibase, num processo da publicação do PRA (CCDR LVT) e do PSA (CIM)
 * criado pelos endpoints: a estrutura documental, a completude que a submissão verifica (no serviço e no
 * {@code PATCH /submeter} com o formulário SGIFR) e a regra de nomes do SGIFR no carregamento, contra o PostgreSQL
 * migrado pelo {@code master.xml} e o storage real.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItensDocumentaisSgifrPostgresIntegrationTest extends StorageTestcontainersBase {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    /** `incm_act_type_id_serie_2` do Despacho (seed `tipo_ato`), o ato de 2.ª série autorizado nas sessões. */
    private static final long ID_INCM_DESPACHO = 11L;
    private static final byte[] CONTEUDO = "conteúdo de teste".getBytes(StandardCharsets.UTF_8);

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
    private CompletudeDocumentalService completudeDocumentalService;

    @Autowired
    private TransactionTemplate transacao;

    private record Processo(UUID id, RequestPostProcessor autor) {
    }

    @Test
    void oProcessoDoPsaMostraOTextoDoAtoAsSeisCartasEOProgramaComAsDuasCaixasEDuasPendencias() throws Exception {
        Processo psa = processo(CODIGO_CAMARA_DE_SINTRA, "CIM", "H", "PSA");

        JsonNode estrutura = estrutura(psa);

        List<String> separadores = new ArrayList<>();
        estrutura.path("separadores").forEach(separador -> {
            separadores.add(separador.path("separador").asText());
            assertThat(separador.path("publicacao")).hasSize(1);
            assertThat(separador.path("deposito")).hasSize(1);
            assertThat(separador.path("publicacao").get(0).path("codigo").asText()).endsWith("_PUB");
            assertThat(separador.path("deposito").get(0).path("codigo").asText()).endsWith("_DEP");
        });
        assertThat(separadores).containsExactly("Texto do Ato", "Carta do Programa — RSFGC", "Carta do Programa — APPS",
                "Carta do Programa — RVDI", "Carta do Programa — AEMGC", "Carta do Programa — RVF",
                "Carta do Programa — RPA", "Programa");
        List<String> pendencias = new ArrayList<>();
        estrutura.path("pendencias").forEach(pendencia -> pendencias.add(pendencia.path("codigo").asText()
                + ":" + pendencia.path("categoria").asText()));
        assertThat(pendencias).containsExactly("TEXTO_ATO_PUB:PUBLICACAO", "PROGRAMA_PUB:PUBLICACAO");
    }

    @Test
    void oProcessoDoPraSemFicheirosFaltamOTextoDoAtoACartaRpfgcEOProgramaDePublicacao() throws Exception {
        Processo pra = processo(CODIGO_CCDR_LVT, "CCDR_DR", "C", "PRA");

        List<String> separadores = new ArrayList<>();
        estrutura(pra).path("separadores").forEach(separador -> separadores.add(separador.path("separador").asText()
                + ":" + separador.path("publicacao").get(0).path("obrigatorio").asBoolean()
                + ":" + separador.path("deposito").get(0).path("obrigatorio").asBoolean()));
        assertThat(separadores).containsExactly("Texto do Ato:true:false", "Carta do Programa — RPFGC:true:false",
                "Programa:true:false");

        // A mesma verificação que a submissão faz depois do formulário.
        assertThatThrownBy(() -> transacao.executeWithoutResult(estado ->
                completudeDocumentalService.verificar(processoRepository.findById(pra.id()).orElseThrow())))
                .isInstanceOfSatisfying(DocumentosObrigatoriosEmFaltaException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("DOCUMENTOS_OBRIGATORIOS_EM_FALTA");
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly(
                            "documentos.TEXTO_ATO_PUB", "documentos.CARTA_RPFGC_PUB", "documentos.PROGRAMA_PUB");
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::erro)
                            .allSatisfy(mensagem -> assertThat(mensagem).endsWith("(PUBLICACAO)."));
                });
    }

    /** Critério 10 ponta a ponta: com o formulário SGIFR da #249 preenchido, a submissão chega à completude documental. */
    @Test
    void oPraComOFormularioSgifrPreenchidoESemFicheirosRecebe400ComOsTresItensDePublicacao() throws Exception {
        Processo pra = processo(CODIGO_CCDR_LVT, "CCDR_DR", "C", "PRA");
        mockMvc.perform(put("/api/v1/processos/" + pra.id() + "/dados-formulario").with(pra.autor())
                        .contentType("application/json").content(formulario(pra, "")))
                .andExpect(status().isOk());

        submeter(pra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(contains(
                        "documentos.TEXTO_ATO_PUB", "documentos.CARTA_RPFGC_PUB", "documentos.PROGRAMA_PUB")));
        assertThat(estadoDe(pra)).isEqualTo("EM_SUBMISSAO");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo_documento WHERE processo_id = ?",
                Integer.class, pra.id())).isZero();
    }

    @Test
    void oTextoDoAtoAceitaONomeDoSgifrERecusaOHifenEAExtensaoForaDaLista() throws Exception {
        Processo psa = processo(CODIGO_CAMARA_DE_SINTRA, "CIM", "H", "PSA");
        long textoDoAto = itemDoProcesso(psa, "TEXTO_ATO_PUB");

        carregar(psa, textoDoAto, "PSA_AML_ATO.docx").andExpect(status().isCreated());

        carregar(psa, textoDoAto, "PSA-AML-ATO.docx")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));
        carregar(psa, textoDoAto, "PSA_AML_ATO.exe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo_documento WHERE processo_id = ?",
                Integer.class, psa.id())).isEqualTo(1);
        List<String> pendencias = new ArrayList<>();
        estrutura(psa).path("pendencias").forEach(pendencia -> pendencias.add(pendencia.path("codigo").asText()));
        assertThat(pendencias).containsExactly("PROGRAMA_PUB");
    }

    // --- Cenário ---

    private RequestPostProcessor sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-itens-sgifr.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", ID_INCM_DESPACHO, "nome", "Despacho", "extrato", false))));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade));
    }

    /** Programa novo, Delimitação e processo da publicação com o Despacho, criados pela entidade pelos endpoints. */
    private Processo processo(String codigoEntidade, String tipoEntidade, String letraParte, String programa) throws Exception {
        RequestPostProcessor autor = sessao(codigoEntidade, tipoEntidade, letraParte);
        long tipoInstrumentoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class,
                programa);
        UUID instrumento = id(mockMvc.perform(post("/api/v1/instrumentos").with(autor).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId,
                        "designacao", programa + " de teste " + UUID.randomUUID(), "concelhos", List.of("1111")))))
                .andExpect(status().isCreated()));
        long dinamicaConfigId = jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = 'DELIMITACAO'", Long.class, programa);
        UUID dinamica = id(mockMvc.perform(post("/api/v1/instrumentos/" + instrumento + "/dinamicas").with(autor)
                .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                        "instrumentoDinamicaConfigId", dinamicaConfigId, "descritivo", "Delimitação do " + programa))))
                .andExpect(status().isCreated()));
        long publicacao = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ipc.instrumento_dinamica_config_id = ? AND tp.codigo = 'PUBLICACAO_SGIFR'", Long.class, dinamicaConfigId);
        long despacho = jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class,
                ID_INCM_DESPACHO);
        UUID processo = id(mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(autor)
                .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                        "instrumentoProcedimentoConfigId", publicacao, "tipoAtoId", despacho))))
                .andExpect(status().isCreated()));
        return new Processo(processo, autor);
    }

    private UUID id(ResultActions resultado) throws Exception {
        return UUID.fromString(objectMapper.readTree(resultado.andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("id").asText());
    }

    private JsonNode estrutura(Processo processo) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/processos/" + processo.id() + "/estrutura-documental")
                        .with(processo.autor()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Os campos do cabeçalho do formulário SGIFR; o PRA grava a rede primária sem a escolher (#249). */
    private String formulario(Processo processo, String extras) {
        long versao = jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo.id());
        return "{\"versao\":" + versao + ",\"sumario\":\"Programa Regional de Ação de teste.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"entidadeEmitente\":\"CCDR LVT\","
                + "\"entidadeInstrutora\":\"CCDR LVT\"" + (extras.isEmpty() ? "" : "," + extras) + "}";
    }

    private ResultActions submeter(Processo processo) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processo.id() + "/submeter").with(processo.autor())
                .contentType("application/json").content(formulario(processo, PedidosSubmissao.JSON_CONFIRMACOES)));
    }

    private String estadoDe(Processo processo) {
        return jdbcTemplate.queryForObject("SELECT estado FROM processo WHERE id = ?", String.class, processo.id());
    }

    private long itemDoProcesso(Processo processo, String codigo) {
        return jdbcTemplate.queryForObject("SELECT d.id FROM documento_config d JOIN processo p "
                + "ON p.instrumento_procedimento_config_id = d.instrumento_procedimento_config_id "
                + "WHERE p.id = ? AND d.codigo = ?", Long.class, processo.id(), codigo);
    }

    private ResultActions carregar(Processo processo, long documentoConfigId, String nome) throws Exception {
        MockMultipartFile ficheiro = new MockMultipartFile("ficheiro", nome, "application/octet-stream",
                ZipsDeTeste.conteudo(nome, CONTEUDO));
        return mockMvc.perform(multipart("/api/v1/processos/" + processo.id() + "/documentos")
                .file(ficheiro)
                .param("categoria", "PUBLICACAO")
                .param("documentoConfigId", String.valueOf(documentoConfigId))
                .with(processo.autor()));
    }
}
