package com.conkord.saigt.db;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * A série escolhida no processo da Delimitação de uma servidão (Procedimento `PUBLICACAO_SRUP` sem série), contra o
 * PostgreSQL migrado pelo {@code master.xml} e o catálogo `tipo_ato` semeado: a escolha, os tipos de ato da série
 * efetiva, a parte do DR da adesão, a submissão e o bloqueio, pelos endpoints.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SerieEscolhidaNoProcessoPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String URL_PROCESSOS = "/api/v1/processos/";
    private static final String SERVIDAO = "SERVIDAO";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    /** `incm_act_type_id_serie_2` e `_serie_1` do Despacho (seed `tipo_ato`). */
    private static final long ID_INCM_DESPACHO_SERIE_2 = 11L;
    private static final long ID_INCM_DESPACHO_SERIE_1 = 37L;
    private static final List<String> TIPOS_DA_PRIMEIRA_SERIE = List.of("Aviso", "Declaração", "Declaração de Retificação",
            "Decreto", "Decreto Legislativo Regional", "Decreto Regulamentar", "Decreto Regulamentar Regional", "Decreto-Lei",
            "Despacho", "Lei", "Portaria");
    private static final List<String> TIPOS_DA_SEGUNDA_SERIE = List.of("Anúncio", "Aviso", "Contrato", "Declaração",
            "Declaração de Retificação", "Deliberação", "Despacho", "Edital", "Portaria");

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

    @Test
    void oProcessoNasceSemSerieComAsTresOpcoesEODisponivelTrazOsTiposComAsSuasSeries() throws Exception {
        RequestPostProcessor camara = sessao("H");
        UUID servidao = criarServidao(camara);
        UUID processo = criarProcessoDaDelimitacao(camara, servidao);

        mockMvc.perform(get(URL_PROCESSOS + processo).with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieDr").value(nullValue()))
                .andExpect(jsonPath("$.seriesDrEscolhiveis").value(contains("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C")))
                .andExpect(jsonPath("$.tiposAtoDisponiveis", empty()));

        // A sessão só tem o Despacho da 2.ª série, na parte H: o tipo vem com as séries em que o pode publicar.
        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + servidao + "/disponivel").with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].serieDr").value(nullValue()))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tiposAtoDisponiveis[*].nome")
                        .value(contains("Despacho")))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tiposAtoDisponiveis[0].seriesDr")
                        .value(contains("SEGUNDA_H")));
    }

    @Test
    void escolhidaASerieOInternoVeOsTiposDaPrimeiraEDepoisOsDaSegunda() throws Exception {
        RequestPostProcessor camara = sessao("H");
        RequestPostProcessor interno = sessaoInterna();
        UUID processo = criarProcessoDaDelimitacao(camara, criarServidao(camara));

        gravar(camara, processo, Map.of("serieDr", "PRIMEIRA")).andExpect(status().isOk());

        mockMvc.perform(get(URL_PROCESSOS + processo).with(interno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieDr").value("PRIMEIRA"))
                .andExpect(jsonPath("$.tiposAtoDisponiveis[*].nome")
                        .value(containsInAnyOrder(TIPOS_DA_PRIMEIRA_SERIE.toArray())));
        assertThat(texto("SELECT serie_dr FROM processo WHERE id = '" + processo + "'")).isEqualTo("PRIMEIRA");

        gravar(camara, processo, Map.of("serieDr", "SEGUNDA_H")).andExpect(status().isOk());

        mockMvc.perform(get(URL_PROCESSOS + processo).with(interno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieDr").value("SEGUNDA_H"))
                .andExpect(jsonPath("$.tiposAtoDisponiveis[*].nome")
                        .value(containsInAnyOrder(TIPOS_DA_SEGUNDA_SERIE.toArray())));
    }

    @Test
    void umaEntidadeDaParteCNaoTemTiposNaSegundaHNemSubmeteNelaMasTemNaSegundaC() throws Exception {
        RequestPostProcessor parteC = sessao("C");
        UUID processo = criarProcessoDaDelimitacao(parteC, criarServidao(parteC));

        gravar(parteC, processo, Map.of("serieDr", "SEGUNDA_H")).andExpect(status().isOk());
        mockMvc.perform(get(URL_PROCESSOS + processo).with(parteC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposAtoDisponiveis", empty()));
        submeter(parteC, processo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PARTE_DR_NAO_AUTORIZADA"));

        gravar(parteC, processo, Map.of("serieDr", "SEGUNDA_C")).andExpect(status().isOk());
        mockMvc.perform(get(URL_PROCESSOS + processo).with(parteC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposAtoDisponiveis[*].nome").value(contains("Despacho")));
    }

    @Test
    void recusaUmaSerieForaDasTresSemMudarNada() throws Exception {
        RequestPostProcessor camara = sessao("H");
        UUID processo = criarProcessoDaDelimitacao(camara, criarServidao(camara));
        gravar(camara, processo, Map.of("serieDr", "SEGUNDA_H")).andExpect(status().isOk());

        gravar(camara, processo, Map.of("serieDr", "OUTRA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("serieDr")));
        assertThat(texto("SELECT serie_dr FROM processo WHERE id = '" + processo + "'")).isEqualTo("SEGUNDA_H");
    }

    @Test
    void naoSubmeteSemSerieSubmeteDepoisDeEscolherASerieEOTipoEDepoisASerieFicaBloqueada() throws Exception {
        RequestPostProcessor camara = sessao("H");
        UUID processo = criarProcessoDaDelimitacao(camara, criarServidao(camara));
        gravar(camara, processo, Map.of("tipoAtoId", despacho())).andExpect(status().isForbidden());

        submeter(camara, processo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("serieDr")))
                .andExpect(jsonPath("$.campos[?(@.path == 'serieDr')].erro").value(contains("Preencha os dados de publicação.")));
        assertThat(texto("SELECT estado FROM processo WHERE id = '" + processo + "'")).isEqualTo("EM_SUBMISSAO");

        gravar(camara, processo, Map.of("serieDr", "SEGUNDA_H", "tipoAtoId", despacho())).andExpect(status().isOk());
        submeter(camara, processo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        jdbcTemplate.update("UPDATE processo SET estado = 'EM_SUBMISSAO' WHERE id = ?", processo);
        mockMvc.perform(get(URL_PROCESSOS + processo).with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem("serieDr")));
        gravar(camara, processo, Map.of("serieDr", "SEGUNDA_C"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("serieDr")));
        assertThat(texto("SELECT serie_dr FROM processo WHERE id = '" + processo + "'")).isEqualTo("SEGUNDA_H");
    }

    @Test
    void mudarParaASeriePrimeiraLimpaUmTipoQueNelaNaoExiste() throws Exception {
        RequestPostProcessor camara = sessaoComAsDuasSeries();
        UUID processo = criarProcessoDaDelimitacao(camara, criarServidao(camara));
        long edital = jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE codigo = 'EDITAL'", Long.class);
        jdbcTemplate.update("UPDATE processo SET serie_dr = 'SEGUNDA_H', tipo_ato_id = ? WHERE id = ?", edital, processo);

        gravar(camara, processo, Map.of("serieDr", "PRIMEIRA", "tipoAtoId", edital)).andExpect(status().isOk());

        assertThat(texto("SELECT serie_dr || ':' || coalesce(tipo_ato_id::text, '-') FROM processo WHERE id = '"
                + processo + "'")).isEqualTo("PRIMEIRA:-");

        // O Despacho existe nas duas séries: continua escolhido ao voltar à 2.ª.
        gravar(camara, processo, Map.of("tipoAtoId", despacho())).andExpect(status().isOk());
        gravar(camara, processo, Map.of("serieDr", "SEGUNDA_H")).andExpect(status().isOk());
        mockMvc.perform(get(URL_PROCESSOS + processo).with(camara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAtoNome").value("Despacho"))
                .andExpect(jsonPath("$.tiposAtoDisponiveis[*].nome").value(not(hasItem("Decreto"))));
    }

    private RequestPostProcessor sessao(String parte) {
        return sessao(parte, List.of());
    }

    private RequestPostProcessor sessaoComAsDuasSeries() {
        long edital = jdbcTemplate.queryForObject("SELECT incm_act_type_id_serie_2 FROM tipo_ato WHERE codigo = 'EDITAL'",
                Long.class);
        return sessao("H", List.of(ID_INCM_DESPACHO_SERIE_1), edital);
    }

    private RequestPostProcessor sessao(String parte, List<Long> serie1, long... maisDaSerie2) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-serie.pt");
        List<Map<String, Object>> serie2 = new java.util.ArrayList<>();
        serie2.add(Map.of("id", ID_INCM_DESPACHO_SERIE_2, "nome", "Despacho", "extrato", false));
        for (long id : maisDaSerie2) {
            serie2.add(Map.of("id", id, "nome", "Ato " + id, "extrato", false));
        }
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", "CM", "nif", nipc,
                "nome", "Entidade de teste", "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", parte, "descricao", "Parte " + parte),
                "atos_autorizados", Map.of("serie_1", serie1, "serie_2", serie2));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", entidade));
    }

    private RequestPostProcessor sessaoInterna() {
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao, perfil_interno) VALUES (?, ?, 'AD_DGT', 'TECNICO')",
                utilizadorId, "tecnico-" + utilizadorId + "@teste-serie.pt");
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO"));
    }

    private UUID criarServidao(RequestPostProcessor quem) throws Exception {
        long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, SERVIDAO);
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoId,
                "designacao", "Servidão de teste " + UUID.randomUUID(), "concelhos", List.of("1111")));
        return id(mockMvc.perform(post(URL_INSTRUMENTOS).with(quem).contentType("application/json").content(pedido))
                .andExpect(status().isCreated()));
    }

    private UUID criarProcessoDaDelimitacao(RequestPostProcessor quem, UUID servidao) throws Exception {
        long dinamicaConfigId = jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = 'DELIMITACAO'", Long.class, SERVIDAO);
        UUID dinamica = id(mockMvc.perform(post(URL_INSTRUMENTOS + "/" + servidao + "/dinamicas").with(quem)
                        .contentType("application/json").content(objectMapper.writeValueAsString(
                                Map.of("instrumentoDinamicaConfigId", dinamicaConfigId, "descritivo", "Delimitação"))))
                .andExpect(status().isCreated()));
        long procedimento = jdbcTemplate.queryForObject("SELECT id FROM instrumento_procedimento_config "
                + "WHERE instrumento_dinamica_config_id = ?", Long.class, dinamicaConfigId);
        return id(mockMvc.perform(post("/api/v1/dinamicas/" + dinamica + "/processos").with(quem)
                        .contentType("application/json").content(objectMapper.writeValueAsString(
                                Map.of("instrumentoProcedimentoConfigId", procedimento))))
                .andExpect(status().isCreated()));
    }

    private ResultActions gravar(RequestPostProcessor quem, UUID processo, Map<String, Object> campos) throws Exception {
        Map<String, Object> pedido = new HashMap<>(campos);
        pedido.put("versao", versao(processo));
        pedido.put("sumario", "Delimitação da servidão de teste.");
        pedido.put("normaLegal", "Decreto-Lei n.º 1/2026");
        pedido.put("tipoInformacao", "TEXTO_INTEGRAL");
        pedido.put("contactoTramitacao", "tramitacao@teste-serie.pt");
        pedido.put("entidadeEmitente", "Câmara Municipal de Teste");
        pedido.put("contactoPublicacao", "publicacao@teste-serie.pt");
        return mockMvc.perform(put(URL_PROCESSOS + processo + "/dados-formulario").with(quem)
                .contentType("application/json").content(objectMapper.writeValueAsString(pedido)));
    }

    private ResultActions submeter(RequestPostProcessor quem, UUID processo) throws Exception {
        Map<String, Object> pedido = Map.of("versao", versao(processo), "sumario", "Delimitação da servidão de teste.",
                "dataAssinatura", "2026-11-02", "normaLegal", "Decreto-Lei n.º 1/2026", "tipoInformacao", "TEXTO_INTEGRAL",
                "confirmacoes", List.of("CONTEUDO_PROCESSO"));
        return mockMvc.perform(patch(URL_PROCESSOS + processo + "/submeter").with(quem)
                .contentType("application/json").content(objectMapper.writeValueAsString(pedido)));
    }

    private long versao(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo);
    }

    private long despacho() {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE incm_act_type_id_serie_2 = ?", Long.class,
                ID_INCM_DESPACHO_SERIE_2);
    }

    private UUID id(ResultActions resposta) throws Exception {
        JsonNode corpo = objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString());
        return UUID.fromString(corpo.get("id").asText());
    }

    private String texto(String consulta) {
        return jdbcTemplate.queryForObject(consulta, String.class);
    }
}
