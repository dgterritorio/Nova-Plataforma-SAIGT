package com.conkord.saigt.db;

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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A matriz de tipos de entidade por configuração de Procedimento, contra o PostgreSQL com todas as migrações: cada
 * configuração tem exatamente os tipos da matriz, calculada pelos códigos, e a criação pela API segue-a. Todos os
 * tipos de Instrumento da matriz já têm configuração no seed: um que a perca faz falhar o caso, não o suspende.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class MatrizPermissaoAcaoPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String URL_DINAMICAS_SEM_IGT = "/api/v1/dinamicas";
    private static final String MENSAGEM_SEM_PERMISSAO_NO_TIPO = "Tipo da entidade da sessão sem permissão para o tipo de Instrumento.";
    private static final String CIM_DO_ALGARVE = "2001";
    private static final String CAMARA_DE_SINTRA = "5245";
    private static final String CCDR_LVT = "8001";
    private static final String APA = "1001";
    private static final String GABINETE_DA_MINISTRA_DA_COESAO = "3002";
    private static final String AREA_METROPOLITANA_DE_LISBOA = "7001";
    private static final String INFRAESTRUTURAS_DE_PORTUGAL = "1002";
    private static final String DGT = "6001";
    private static final long ID_INCM_DESPACHO = 11L;

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
    private DataSource dataSource;

    @Test
    void cadaConfiguracaoDeveTerExatamenteOsTiposDeEntidadeDaMatrizEPcmSoNa1aSerie() throws Exception {
        try (Connection ligacao = dataSource.getConnection()) {
            assertThat(MatrizPermissaoAcao.configuracoes(ligacao)).isPositive();
            assertThat(MatrizPermissaoAcao.desacordos(ligacao)).as("configurações em desacordo com a matriz").isEmpty();
        }
    }

    @Test
    void aCcdrLvtNaoCriaUmPdm() throws Exception {
        criar(sessao(CCDR_LVT), "PDM", recusado());
    }

    @Test
    void oGabineteDaMinistraNaoCriaUmPdm() throws Exception {
        criar(sessao(GABINETE_DA_MINISTRA_DA_COESAO), "PDM", recusado());
    }

    @Test
    void oGabineteDaMinistraNaoCriaUmProt() throws Exception {
        criar(sessao(GABINETE_DA_MINISTRA_DA_COESAO), "PROT", recusado());
    }

    @Test
    void oGabineteDaMinistraIniciaUmaRatificacaoEACamaraDeSintraNao() throws Exception {
        long ratificacao = dinamicaConfigId("PDM", "RATIFICACAO");
        iniciarSemIgt(sessao(GABINETE_DA_MINISTRA_DA_COESAO), ratificacao, status().isCreated());
        iniciarSemIgt(sessao(CAMARA_DE_SINTRA), ratificacao, status().isForbidden());
    }

    @Test
    void aDgtCriaCadaTipoDeInstrumentoConfigurado() throws Exception {
        List<String> tipos = tiposConfigurados();
        assertThat(tipos).contains("PDM", "PU", "PP");
        RequestPostProcessor dgt = sessao(DGT);
        for (String tipo : tipos) {
            criar(dgt, tipo, status().isCreated());
        }
    }

    /** O BackOffice só muda `ativo`: sem a linha da DGT no Procedimento inicial do PU, a DGT deixa de criar PU. */
    @Test
    void aLinhaDesativadaRecusaACriacaoSemAlterarCodigo() throws Exception {
        String linhasDaDgtNoPu = "UPDATE permissao_acao pa SET ativo = ? FROM instrumento_procedimento_config c, "
                + "instrumento_dinamica_config d, tipo_instrumento ti WHERE c.id = pa.instrumento_procedimento_config_id "
                + "AND d.id = c.instrumento_dinamica_config_id AND ti.id = d.tipo_instrumento_id "
                + "AND ti.codigo = 'PU' AND pa.tipo_entidade = 'DGT'";
        RequestPostProcessor dgt = sessao(DGT);
        jdbcTemplate.update(linhasDaDgtNoPu, false);
        try {
            criar(dgt, "PU", recusado());
            criar(dgt, "PP", status().isCreated());
        } finally {
            jdbcTemplate.update(linhasDaDgtNoPu, true);
        }
        criar(dgt, "PU", status().isCreated());
    }

    @Test
    void aCimDoAlgarveCriaUmPimotEIniciaAElaboracaoEACamaraDeSintraNao() throws Exception {
        UUID pimot = criar(sessao(CIM_DO_ALGARVE), "PIMOT", status().isCreated());
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pimot + "/dinamicas").with(sessao(CIM_DO_ALGARVE))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId",
                                dinamicaConfigId("PIMOT", "ELABORACAO"), "descritivo", "Elaboração do PIMOT"))))
                .andExpect(status().isCreated());
        criar(sessao(CAMARA_DE_SINTRA), "PIMOT", recusado());
    }

    @Test
    void aLinhaCimDoPimotDesativadaRecusaACriacaoPelaCim() throws Exception {
        String linhasDaCimNoPimot = "UPDATE permissao_acao pa SET ativo = ? FROM instrumento_procedimento_config c, "
                + "instrumento_dinamica_config d, tipo_instrumento ti WHERE c.id = pa.instrumento_procedimento_config_id "
                + "AND d.id = c.instrumento_dinamica_config_id AND ti.id = d.tipo_instrumento_id "
                + "AND ti.codigo = 'PIMOT' AND pa.tipo_entidade = 'CIM'";
        jdbcTemplate.update(linhasDaCimNoPimot, false);
        try {
            criar(sessao(CIM_DO_ALGARVE), "PIMOT", recusado());
        } finally {
            jdbcTemplate.update(linhasDaCimNoPimot, true);
        }
    }

    @Test
    void aCcdrLvtCriaUmProt() throws Exception {
        criar(sessao(CCDR_LVT), "PROT", status().isCreated());
    }

    @Test
    void aApaCriaUmPeotENaoUmProt() throws Exception {
        criar(sessao(APA), "PEOT", status().isCreated());
        criar(sessao(APA), "PROT", recusado());
    }

    @Test
    void aAreaMetropolitanaDeLisboaCriaUmPsaENaoUmPra() throws Exception {
        criar(sessao(AREA_METROPOLITANA_DE_LISBOA), "PSA", status().isCreated());
        criar(sessao(AREA_METROPOLITANA_DE_LISBOA), "PRA", recusado());
    }

    @Test
    void asInfraestruturasDePortugalCriamUmaDasRestantesServidoes() throws Exception {
        List<String> servidoes = jdbcTemplate.queryForList("SELECT DISTINCT ti.codigo FROM tipo_instrumento ti "
                + "JOIN instrumento_dinamica_config d ON d.tipo_instrumento_id = ti.id AND d.ativo "
                + "WHERE ti.categoria = 'SRUP' AND ti.codigo NOT IN ('REN', 'PRA', 'PSA') ORDER BY 1", String.class);
        assertThat(servidoes).isNotEmpty();
        criar(sessao(INFRAESTRUTURAS_DE_PORTUGAL), servidoes.get(0), status().isCreated());
    }

    private static ResultMatcher recusado() {
        return result -> {
            status().isForbidden().match(result);
            jsonPath("$.codigo").value("SEM_PERMISSAO").match(result);
            jsonPath("$.mensagem").value(MENSAGEM_SEM_PERMISSAO_NO_TIPO).match(result);
        };
    }

    /** Uma sessão INCM na entidade do código, com o tipo que a entidade tem na base. */
    private RequestPostProcessor sessao(String codigoEntidade) {
        Map<String, Object> linha = jdbcTemplate.queryForMap("SELECT id, nipc, tipo_entidade FROM entidade WHERE codigo = ?",
                codigoEntidade);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-matriz.pt");
        Map<String, Object> entidade = Map.of("id", linha.get("id"), "tipo_entidade", linha.get("tipo_entidade"),
                "nif", linha.get("nipc"), "nome", "Entidade de teste " + codigoEntidade, "pcm", false,
                "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"),
                "atos_autorizados", Map.of("serie_1", List.of(),
                        "serie_2", List.of(Map.of("id", ID_INCM_DESPACHO, "nome", "Despacho", "extrato", false))));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM").claim("entidade", entidade));
    }

    private UUID criar(RequestPostProcessor sessao, String tipoInstrumento, ResultMatcher esperado) throws Exception {
        Map<String, Object> tipo = jdbcTemplate.queryForMap("SELECT id, ambito_territorial FROM tipo_instrumento WHERE codigo = ?",
                tipoInstrumento);
        int numeroDeConcelhos = "INTERMUNICIPAL".equals(tipo.get("ambito_territorial")) ? 2 : 1;
        List<String> concelhos = jdbcTemplate.queryForList("SELECT dico FROM entidade WHERE tipo_entidade = 'CM' "
                + "AND dico IS NOT NULL AND ativo ORDER BY dico LIMIT ?", String.class, numeroDeConcelhos);
        String pedido = objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipo.get("id"),
                "designacao", tipoInstrumento + " da matriz " + UUID.randomUUID(), "concelhos", concelhos));
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(sessao).contentType("application/json").content(pedido))
                .andExpect(esperado)
                .andReturn().getResponse().getContentAsString();
        return resposta.contains("\"id\"") ? UUID.fromString(objectMapper.readTree(resposta).get("id").asText()) : null;
    }

    private void iniciarSemIgt(RequestPostProcessor sessao, long instrumentoDinamicaConfigId, ResultMatcher esperado)
            throws Exception {
        mockMvc.perform(post(URL_DINAMICAS_SEM_IGT).with(sessao).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", instrumentoDinamicaConfigId,
                                "descritivo", "Ratificação " + UUID.randomUUID().toString().substring(0, 8)))))
                .andExpect(esperado);
    }

    private List<String> tiposConfigurados() {
        return jdbcTemplate.queryForList("SELECT DISTINCT ti.codigo FROM tipo_instrumento ti "
                + "JOIN instrumento_dinamica_config d ON d.tipo_instrumento_id = ti.id AND d.ativo ORDER BY 1", String.class);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }
}
