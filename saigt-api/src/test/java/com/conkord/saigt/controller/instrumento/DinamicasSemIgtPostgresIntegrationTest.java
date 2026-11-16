package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
 * Dinâmicas iniciadas sem IGT de partida (Suspensão da CCDR, Suspensão e Medidas Preventivas do Governo, Ratificação,
 * Medidas Preventivas do município), num PostgreSQL migrado pelo {@code master.xml} com o seed das permissões.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class DinamicasSemIgtPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_SEC_ESTADO = "3001";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String CODIGO_DGT = "6001";
    private static final String TIPOS_PROCESSO = "/api/v1/criacao/tipos-processo";
    private static final String DINAMICAS = "/api/v1/dinamicas";

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
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;

    @Test
    void oProcessoDaSuspensaoDoGovernoSobreOPdmDeSintraNaoEVisivelACamaraQueVeADinamicaNoPainelSemEle() throws Exception {
        Instrumento pdmDeSintra = novoInstrumento("PDM", "PDM de Sintra");
        UUID suspensao = dinamicaSemInstrumentoDeInicio("SUSPENSAO_GOVERNO", "Suspensão Sintra " + sufixo());
        abranger(suspensao, pdmDeSintra);
        Sessao governo = sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);

        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.designacaoInstrumento").value(org.hamcrest.Matchers.startsWith("Suspensão Sintra")))
                .andExpect(jsonPath("$.tipoInstrumento").value("PDM"))
                .andExpect(jsonPath("$.categoriaInstrumento").value("PLANO"))
                .andExpect(jsonPath("$.instrumentosAbrangidos[0].id").value(pdmDeSintra.getId().toString()))
                .andExpect(jsonPath("$.instrumentosAbrangidos[0].designacao").value(pdmDeSintra.getDesignacao()))
                .andExpect(jsonPath("$.instrumentosAbrangidos[0].tipoInstrumento").value("PDM"));
        mockMvc.perform(get("/api/v1/processos").with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(processo.toString())));

        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
        mockMvc.perform(get("/api/v1/processos/" + processo).with(camara.jwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/instrumentos/" + pdmDeSintra.getId()).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicas[?(@.id == '" + suspensao + "')].tipoDinamica").value(hasItem("SUSPENSAO_GOVERNO")))
                .andExpect(jsonPath("$.dinamicas[?(@.id == '" + suspensao + "')].procedimentos[*].processos[*]", empty()));
        mockMvc.perform(get("/api/v1/processos").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(hasItem(processo.toString()))));
    }

    @Test
    void umaDinamicaSemNenhumInstrumentoDaAoProcessoODescritivoEUmTerritorioVazio() throws Exception {
        String descritivo = "Suspensão sem IGT " + sufixo();
        UUID suspensao = dinamicaSemInstrumentoDeInicio("SUSPENSAO_GOVERNO", descritivo);
        Sessao governo = sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);

        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designacaoInstrumento").value(descritivo))
                .andExpect(jsonPath("$.numeroInstrumento").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.instrumentosAbrangidos", empty()))
                .andExpect(jsonPath("$.territorio.concelhos", empty()))
                .andExpect(jsonPath("$.idus").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idus_processo WHERE processo_id = ?", Integer.class, processo))
                .isZero();
    }

    // --- Camada 3 e primeiro passo do assistente ---

    @Test
    void oGovernoEACcdrNaoCriamUmPdmEACamaraCria() throws Exception {
        long pdm = tipoInstrumentoId("PDM");
        for (Sessao semDireito : java.util.List.of(sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H"),
                sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C"))) {
            mockMvc.perform(post("/api/v1/instrumentos").with(semDireito.jwt()).contentType("application/json")
                            .content(pedidoDePdm(pdm)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
        mockMvc.perform(post("/api/v1/instrumentos").with(sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H").jwt())
                        .contentType("application/json").content(pedidoDePdm(pdm)))
                .andExpect(status().isCreated());
    }

    @Test
    void aCcdrSoIniciaASuspensaoDaCcdrSemAssociacaoEContinuaOPdmComAssociacaoPelaConstituicaoDaCc() throws Exception {
        mockMvc.perform(get(TIPOS_PROCESSO).with(sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semAssociacaoIgt[*].codigo", contains("SUSPENSAO_CCDR")))
                .andExpect(jsonPath("$.semAssociacaoIgt[0].configuracoes[*].tipoInstrumentoCodigo", hasItem("PDM")))
                .andExpect(jsonPath("$.semAssociacaoIgt[0].configuracoes[*].associacaoInstrumentos", everyItem(is("OPCIONAL"))))
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", hasItems("PDM", "PDIM")))
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", not(hasItem("PU"))));
    }

    @Test
    void oGovernoTemAsDinamicasDoGovernoEARatificacaoEACamaraOsPlanosEAsMedidasPreventivasDoMunicipio() throws Exception {
        mockMvc.perform(get(TIPOS_PROCESSO).with(sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semAssociacaoIgt[*].codigo",
                        containsInAnyOrder("SUSPENSAO_GOVERNO", "MEDIDAS_PREVENTIVAS_GOVERNO", "RATIFICACAO")))
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", not(hasItem("PDM"))));

        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
        mockMvc.perform(get(TIPOS_PROCESSO).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", hasItems("PDM", "PU", "PP")))
                .andExpect(jsonPath("$.semAssociacaoIgt[*].codigo", contains("MEDIDAS_PREVENTIVAS")))
                .andExpect(jsonPath("$.semAssociacaoIgt[0].configuracoes[*].tipoInstrumentoCodigo",
                        containsInAnyOrder("PDM", "PU", "PP", "PDIM", "PUIM", "PPIM")));
        // O grupo sem IGT é do módulo Planos.
        mockMvc.perform(get(TIPOS_PROCESSO).param("modulo", "PROGRAMA").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semAssociacaoIgt", empty()))
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", not(hasItem("PDM"))));
        mockMvc.perform(get(TIPOS_PROCESSO).param("modulo", "OUTRO").with(camara.jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("modulo"));
    }

    @Test
    void aEntidadeDgtRecebeTodosEUmMasterSemEntidadeRecebe403() throws Exception {
        mockMvc.perform(get(TIPOS_PROCESSO).with(sessao(CODIGO_DGT, "DGT", "H").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semAssociacaoIgt[*].codigo", hasItems("SUSPENSAO_CCDR", "SUSPENSAO_GOVERNO",
                        "MEDIDAS_PREVENTIVAS_GOVERNO", "RATIFICACAO", "MEDIDAS_PREVENTIVAS")))
                .andExpect(jsonPath("$.comAssociacaoIgt[*].codigo", hasItems("PDM", "PU", "PP", "PNPOT")));
        mockMvc.perform(get(TIPOS_PROCESSO).with(master()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    // --- Início sem IGT ---

    @Test
    void aCcdrIniciaASuspensaoDaCcdrSemInstrumentos() throws Exception {
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
        String resposta = mockMvc.perform(post(DINAMICAS).with(ccdr.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_CCDR", "Suspensão parcial PDM Sintra e Cascais")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.instrumentoIds", empty()))
                .andExpect(jsonPath("$.tipoDinamica").value("SUSPENSAO_CCDR"))
                .andExpect(jsonPath("$.descritivo").value("Suspensão parcial PDM Sintra e Cascais"))
                .andExpect(jsonPath("$.estado").value("EM_CURSO"))
                .andReturn().getResponse().getContentAsString();
        UUID dinamica = UUID.fromString(ler(resposta).get("id").asText());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica_instrumento WHERE dinamica_id = ?",
                Integer.class, dinamica)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT instrumento_id FROM dinamica WHERE id = ?", UUID.class, dinamica)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT entidade_id FROM auditoria WHERE tipo_ocorrencia = 'DINAMICA' "
                + "AND id_ocorrencia = ? AND acao = 'CRIACAO'", Long.class, dinamica.toString())).isEqualTo(ccdr.entidadeId());
    }

    @Test
    void aSuspensaoDoGovernoIniciaSeSemInstrumentosEADesignacaoNaoSeRepeteNoMesmoTipo() throws Exception {
        Sessao governo = sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H");
        mockMvc.perform(post(DINAMICAS).with(governo.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_GOVERNO", "Suspensão Sintra")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoIds", empty()));

        mockMvc.perform(post(DINAMICAS).with(governo.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PROT", "SUSPENSAO_GOVERNO", " suspensão sintra ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DESIGNACAO_DUPLICADA"))
                .andExpect(jsonPath("$.mensagem").value("Já existe uma dinâmica deste tipo com esta designação."))
                .andExpect(jsonPath("$.campos[0].path").value("descritivo"));
        mockMvc.perform(post(DINAMICAS).with(governo.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "MEDIDAS_PREVENTIVAS_GOVERNO", "Suspensão Sintra")))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica WHERE lower(trim(descritivo)) = 'suspensão sintra'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void aCcdrNaoIniciaAsMedidasPreventivasDoGovernoENenhumaDinamicaQueSeIniciaPeloIgt() throws Exception {
        mockMvc.perform(post(DINAMICAS).with(sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C").jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "MEDIDAS_PREVENTIVAS_GOVERNO", "MP do Governo " + sufixo())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(post(DINAMICAS).with(sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H").jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "ALTERACAO", "Alteração " + sufixo())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"))
                .andExpect(jsonPath("$.mensagem").value("Esta dinâmica inicia-se a partir do IGT."));
        mockMvc.perform(post(DINAMICAS).with(master()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_GOVERNO", "Pelo Master " + sufixo())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(DINAMICAS).with(sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H").jwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", 999_999, "descritivo", "X"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(DINAMICAS).with(sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H").jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_GOVERNO", "x".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("descritivo"));
    }

    // --- Medidas Preventivas do município sem IGT ---

    @Test
    void aCamaraIniciaAsMedidasPreventivasSemIgtECriaGravaESubmeteAPublicacaoSemIdus() throws Exception {
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
        String descritivo = "MP Sintra " + sufixo();
        String resposta = mockMvc.perform(post(DINAMICAS).with(camara.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "MEDIDAS_PREVENTIVAS", descritivo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.instrumentoIds", empty()))
                .andReturn().getResponse().getContentAsString();
        UUID medidas = UUID.fromString(ler(resposta).get("id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica_instrumento WHERE dinamica_id = ?",
                Integer.class, medidas)).isZero();

        UUID processo = criarProcesso(medidas, "MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP", camara);
        // Sem ficheiros no teste: os itens obrigatórios levam observações para a submissão passar (FR-137).
        jdbcTemplate.update("INSERT INTO processo_documento_observacao (processo_id, documento_config_id, observacoes) "
                + "SELECT p.id, d.id, 'Sem ficheiro no teste.' FROM processo p JOIN documento_config d "
                + "ON d.instrumento_procedimento_config_id = p.instrumento_procedimento_config_id "
                + "WHERE p.id = ? AND d.obrigatorio AND d.ativo", processo);
        mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(camara.jwt())
                        .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                                + ",\"entidadeEmitente\":\"Câmara Municipal de Sintra\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"],"
                                + "\"localizacao\":{\"aplicavelTodaArea\":true}}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(true));
        mockMvc.perform(patch("/api/v1/processos/" + processo + "/submeter").with(camara.jwt())
                        .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                                + "," + com.conkord.saigt.dto.instrumento.PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        mockMvc.perform(get("/api/v1/processos/" + processo).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"))
                .andExpect(jsonPath("$.idus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.instrumentoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.designacaoInstrumento").value(descritivo))
                .andExpect(jsonPath("$.tipoInstrumento").value("PDM"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idus_processo WHERE processo_id = ?", Integer.class,
                processo)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT instrumento_id FROM dinamica WHERE id = ?", UUID.class, medidas)).isNull();
    }

    @Test
    void asMedidasPreventivasSemIgtComAConfiguracaoDoPdmTemOsItensDaPublicacaoDasMedidasPreventivasDoPdm() throws Exception {
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
        String resposta = mockMvc.perform(post(DINAMICAS).with(camara.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "MEDIDAS_PREVENTIVAS", "MP Sintra " + sufixo())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID processo = criarProcesso(UUID.fromString(ler(resposta).get("id").asText()), "MEDIDAS_PREVENTIVAS",
                "PUBLICACAO_MP", camara);
        mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(camara.jwt())
                        .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                                + ",\"entidadeEmitente\":\"Câmara Municipal de Sintra\","
                                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\",\"PECAS_GRAFICAS\",\"OUTROS\"],"
                                + "\"localizacao\":{\"aplicavelTodaArea\":true}}"))
                .andExpect(status().isOk());
        java.util.List<Integer> itensDoPdm = jdbcTemplate.queryForList("SELECT id FROM documento_config "
                + "WHERE instrumento_procedimento_config_id = ? AND ativo AND pagina = 'DOCUMENTOS' ORDER BY ordem",
                Integer.class, procedimentoConfigId("PDM", "MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP"));
        assertThat(itensDoPdm).hasSize(9);

        mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Deliberação",
                        "Texto das Medidas Preventivas", "Planta de Delimitação", "Fundamentação")))
                .andExpect(jsonPath("$.separadores[*].publicacao[*].codigo").value(contains("TEXTO_ATO_PUB",
                        "DELIBERACAO_PUB", "TEXTO_MEDIDAS_PREVENTIVAS_PUB", "PLANTA_DELIMITACAO_PUB")))
                .andExpect(jsonPath("$.separadores[*].deposito[*].codigo").value(contains("TEXTO_ATO_DEP",
                        "DELIBERACAO_DEP", "TEXTO_MEDIDAS_PREVENTIVAS_DEP", "PLANTA_DELIMITACAO_DEP", "FUNDAMENTACAO_DEP")))
                .andExpect(jsonPath("$.separadores..documentoConfigId")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(itensDoPdm.toArray())))
                .andExpect(jsonPath("$.separadores..obrigatorio")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.pendencias", hasSize(9)));
    }

    // --- Dinâmicas sem IGT em curso ---

    @Test
    void aSuspensaoDoGovernoPublicadaVemAEntidadeQueACriouComOsInstrumentosOAtoEAProrrogacaoENaoAOutra() throws Exception {
        Sessao governo = sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H");
        String resposta = mockMvc.perform(post(DINAMICAS).with(governo.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_GOVERNO", "Suspensão em curso " + sufixo())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID suspensao = UUID.fromString(ler(resposta).get("id").asText());
        Instrumento pdmDeSintra = novoInstrumento("PDM", "PDM de Sintra");
        jdbcTemplate.update("INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) VALUES (?, '1111')",
                pdmDeSintra.getId());
        abranger(suspensao, pdmDeSintra);
        UUID publicacao = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO', numero_dr = '215', data_publicacao_dr = DATE '2026-11-05' "
                + "WHERE id = ?", publicacao);
        String daSuspensao = "$.dinamicasEmCurso[?(@.dinamicaId == '" + suspensao + "')]";

        // Outro utilizador da mesma entidade: vê-a pelo processo da entidade.
        mockMvc.perform(get(DINAMICAS + "/disponivel").with(sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daSuspensao + ".tipoDinamica").value(contains("SUSPENSAO_GOVERNO")))
                .andExpect(jsonPath(daSuspensao + ".estado").value(contains("EM_CURSO")))
                .andExpect(jsonPath(daSuspensao + ".instrumentos[*].id").value(contains(pdmDeSintra.getId().toString())))
                .andExpect(jsonPath(daSuspensao + ".instrumentos[*].concelhos[*]").value(contains("1111")))
                .andExpect(jsonPath(daSuspensao + ".atoPublicado.processoId").value(contains(publicacao.toString())))
                .andExpect(jsonPath(daSuspensao + ".atoPublicado.numeroDr").value(contains("215")))
                .andExpect(jsonPath(daSuspensao + ".atoPublicado.dataPublicacaoDr").value(contains("2026-11-05")))
                .andExpect(jsonPath(daSuspensao + ".procedimentosDisponiveis[*].tipoProcedimento")
                        .value(contains("PRORROGACAO_SUSPENSAO_GOVERNO")));

        mockMvc.perform(get(DINAMICAS + "/disponivel").with(sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daSuspensao).value(empty()));
        mockMvc.perform(get(DINAMICAS + "/disponivel").with(sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daSuspensao).value(empty()));
        // A entidade DGT vê todas.
        mockMvc.perform(get(DINAMICAS + "/disponivel").with(sessao(CODIGO_DGT, "DGT", "H").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daSuspensao + ".dinamicaId").value(contains(suspensao.toString())));
    }

    @Test
    void quemIniciouUmaDinamicaSemIgtVeASemProcessosComOProcedimentoInicial() throws Exception {
        Sessao ccdr = sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
        String resposta = mockMvc.perform(post(DINAMICAS).with(ccdr.jwt()).contentType("application/json")
                        .content(pedidoDeDinamica("PDM", "SUSPENSAO_CCDR", "Suspensão CCDR " + sufixo())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String daSuspensao = "$.dinamicasEmCurso[?(@.dinamicaId == '" + ler(resposta).get("id").asText() + "')]";

        mockMvc.perform(get(DINAMICAS + "/disponivel").with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daSuspensao + ".instrumentos[*]").value(empty()))
                .andExpect(jsonPath(daSuspensao + ".atoPublicado").value(contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath(daSuspensao + ".procedimentosDisponiveis[*].tipoProcedimento").value(contains("SUSPENSAO_CCDR")));
    }

    // --- apoio ---

    private static final String CAMPOS_DO_FORMULARIO = "\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
            + java.time.LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";

    private long versao(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo);
    }

    private String pedidoDeDinamica(String tipoInstrumento, String tipoDinamica, String descritivo) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoInstrumento, tipoDinamica),
                "descritivo", descritivo));
    }

    private String pedidoDePdm(long tipoInstrumentoId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId,
                "designacao", "PDM novo " + sufixo(), "concelhos", java.util.List.of("1111")));
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private RequestPostProcessor master() {
        String conta = "master-" + UUID.randomUUID();
        Utilizador master = new Utilizador(conta, conta + "@dgt.pt", "AD_DGT");
        master.setPerfilInterno(com.conkord.saigt.persistence.operacional.PerfilInterno.MASTER);
        String sub = utilizadorRepository.saveAndFlush(master).getId().toString();
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private Instrumento novoInstrumento(String tipo, String designacao) {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável", "responsavel-" + UUID.randomUUID() + "@teste-sem-igt.pt", "INCM"));
        String numero = tipo + "-" + sufixo();
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> tipo.equals(t.getCodigo())).findFirst().orElseThrow(), numero, designacao + " " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(true);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    /** Como a #485 a deixará antes da associação: sem Instrumento de início nem linhas em `dinamica_instrumento`. */
    private UUID dinamicaSemInstrumentoDeInicio(String tipoDinamica, String descritivo) {
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor", "autor-" + UUID.randomUUID() + "@teste-sem-igt.pt", "INCM"));
        return dinamicaRepository.saveAndFlush(new Dinamica(
                instrumentoDinamicaConfigRepository.findById(dinamicaConfigId("PDM", tipoDinamica)).orElseThrow(),
                descritivo, autor)).getId();
    }

    private void abranger(UUID dinamica, Instrumento instrumento) {
        jdbcTemplate.update("INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) VALUES (?, ?)",
                dinamica, instrumento.getId());
    }

    private UUID criarProcesso(UUID dinamica, String tipoDinamica, String tipoProcedimento, Sessao sessao) throws Exception {
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(sessao.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", procedimentoConfigId("PDM", tipoDinamica, tipoProcedimento),
                                "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(ler(resposta).get("id").asText());
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoInstrumento, String tipoDinamica, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ipc.instrumento_dinamica_config_id = ? AND tp.codigo = ?", Long.class,
                dinamicaConfigId(tipoInstrumento, tipoDinamica), tipoProcedimento);
    }

    private JsonNode ler(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId, Long entidadeId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-sem-igt.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", entidade)), utilizadorId, entidadeId);
    }
}
