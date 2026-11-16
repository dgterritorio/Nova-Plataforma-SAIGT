package com.conkord.saigt.db;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.regras.AvaliadorRegistoValidator;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O seed dos planos intermunicipais contra o PostgreSQL migrado pelo {@code master.xml}: a configuração semeada e o que
 * a Câmara (Sintra, entidade 5245) e a CCDR LVT (entidade 8001) veem dela pelos endpoints.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedPlanosIntermunicipaisPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final List<String> CONCELHOS_SINTRA_E_CASCAIS = List.of("1111", "1105");
    private static final List<String> PLANOS = List.of("PDIM", "PUIM", "PPIM");
    private static final List<String> DINAMICAS = List.of("ELABORACAO", "REVISAO", "ALTERACAO",
            "ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL", "REVOGACAO");
    /** Sem as Dinâmicas do seed das Medidas Preventivas, Normas Provisórias e Suspensão do município, que vem depois. */
    /** Os atos pontuais vêm de um seed posterior, por tipo de Procedimento. */
    private static final String SEM_OS_ATOS_PONTUAIS = " AND NOT ipc.ato_pontual";
    private static final String SEM_AS_DO_MUNICIPIO = " AND idc.tipo_dinamica_id NOT IN (SELECT id FROM tipo_dinamica "
            + "WHERE codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO') OR inicio_sem_igt)";
    private static final List<String> PROCEDIMENTOS_DAS_ALTERACOES_E_SEGUINTES = List.of(
            "ALTERACAO:1:DECISAO_INICIO:SEGUNDA_H", "ALTERACAO:2:ABERTURA_DISCUSSAO_PUBLICA:SEGUNDA_H",
            "ALTERACAO:3:ALTERACAO:SEGUNDA_H",
            "ALTERACAO_SIMPLIFICADA_ART123:1:DECISAO_INICIO:SEGUNDA_H",
            "ALTERACAO_SIMPLIFICADA_ART123:2:ALTERACAO_SIMPLIFICADA:SEGUNDA_H",
            "ALTERACAO_ADAPTACAO:1:ALTERACAO_ADAPTACAO:SEGUNDA_H",
            "CORRECAO_MATERIAL:1:CORRECAO_MATERIAL:SEGUNDA_H",
            "REVOGACAO:1:REVOGACAO:SEGUNDA_H");

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
    private AvaliadorRegistoValidator avaliadorRegistoValidator;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void deveSemearOsTresPlanosIntermunicipaisComoPlanosEsemOsCodigosAntigos() {
        assertThat(linhas("SELECT codigo || ':' || nome || ':' || categoria || ':' || ativo || ':' || snit_codigo || ':' "
                + "|| ambito_territorial FROM tipo_instrumento WHERE codigo IN ('PDIM', 'PUIM', 'PPIM', 'PUI', 'PPI') ORDER BY id"))
                .containsExactly("PDIM:Plano Diretor Intermunicipal:PLANO:true:53:INTERMUNICIPAL",
                        "PUIM:Plano de Urbanização Intermunicipal:PLANO:true:54:INTERMUNICIPAL",
                        "PPIM:Plano de Pormenor Intermunicipal:PLANO:true:55:INTERMUNICIPAL");
    }

    @Test
    void cadaPlanoDeveTerAsOitoDinamicasAtivasPelaOrdemESemADl10() {
        for (String plano : PLANOS) {
            assertThat(linhas("SELECT td.codigo FROM instrumento_dinamica_config idc "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = ? AND idc.ativo AND idc.associacao_instrumentos = 'UM'" + SEM_AS_DO_MUNICIPIO
                    + " ORDER BY idc.id", plano))
                    .as(plano).containsExactlyElementsOf(DINAMICAS);
        }
        assertThat(contar("SELECT count(*) FROM instrumento_dinamica_config idc "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo IN ('PDIM', 'PUIM', 'PPIM') AND td.codigo = 'ALTERACAO_SIMPLIFICADA_DL10_2024'")).isZero();
    }

    @Test
    void oPdimDeveTer16ProcedimentosComACcNaSegundaSerieParteCEOPuimEOPpim14() {
        List<String> pdim = new ArrayList<>(List.of(
                "ELABORACAO:1:DECISAO_INICIO:SEGUNDA_H", "ELABORACAO:2:CONSTITUICAO_CC:SEGUNDA_C",
                "ELABORACAO:3:ABERTURA_DISCUSSAO_PUBLICA:SEGUNDA_H", "ELABORACAO:4:ELABORACAO:SEGUNDA_H",
                "REVISAO:1:DECISAO_INICIO:SEGUNDA_H", "REVISAO:2:CONSTITUICAO_CC:SEGUNDA_C",
                "REVISAO:3:ABERTURA_DISCUSSAO_PUBLICA:SEGUNDA_H", "REVISAO:4:REVISAO:SEGUNDA_H"));
        pdim.addAll(PROCEDIMENTOS_DAS_ALTERACOES_E_SEGUINTES);
        List<String> puimEPpim = new ArrayList<>(List.of(
                "ELABORACAO:1:DECISAO_INICIO:SEGUNDA_H", "ELABORACAO:2:ABERTURA_DISCUSSAO_PUBLICA:SEGUNDA_H",
                "ELABORACAO:3:ELABORACAO:SEGUNDA_H",
                "REVISAO:1:DECISAO_INICIO:SEGUNDA_H", "REVISAO:2:ABERTURA_DISCUSSAO_PUBLICA:SEGUNDA_H",
                "REVISAO:3:REVISAO:SEGUNDA_H"));
        puimEPpim.addAll(PROCEDIMENTOS_DAS_ALTERACOES_E_SEGUINTES);

        assertThat(procedimentos("PDIM")).hasSize(16).containsExactlyElementsOf(pdim);
        assertThat(procedimentos("PUIM")).hasSize(14).containsExactlyElementsOf(puimEPpim);
        assertThat(procedimentos("PPIM")).hasSize(14).containsExactlyElementsOf(puimEPpim);

        assertThat(linhas("SELECT DISTINCT ipc.obrigatorio || ':' || ipc.ativo || ':' || ipc.ato_pontual || ':' "
                + "|| ipc.tamanho_maximo_processo FROM instrumento_procedimento_config ipc " + DOS_PLANOS))
                .containsExactly("true:true:false:5368709120");
        assertThat(linhas("SELECT DISTINCT tp.codigo FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id " + DOS_PLANOS + " AND ipc.repetivel"))
                .containsExactly("ABERTURA_DISCUSSAO_PUBLICA");
        assertThat(linhas("SELECT ti.codigo || ':' || tp.codigo FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id " + DOS_PLANOS + " AND ipc.visivel_na_dinamica ORDER BY ipc.id"))
                .containsExactly("PDIM:CONSTITUICAO_CC", "PDIM:CONSTITUICAO_CC");
    }

    @Test
    void cadaProcedimentoDeveTerACamaraOuNaCcACcdrEADgtAtivas() {
        assertThat(linhas("SELECT tp.codigo || ':' || string_agg(pa.tipo_entidade || '/' || pa.ativo, ',' ORDER BY pa.tipo_entidade) "
                + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id " + DOS_PLANOS
                + " GROUP BY ipc.id, tp.codigo ORDER BY ipc.id"))
                .hasSize(44)
                .allSatisfy(linha -> assertThat(linha).satisfiesAnyOf(
                        l -> assertThat(l).startsWith("CONSTITUICAO_CC:").endsWith(":CCDR_DR/true,DGT/true"),
                        l -> assertThat(l).doesNotStartWith("CONSTITUICAO_CC:").endsWith(":CM/true,DGT/true")));
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id " + DOS_PLANOS)).isEqualTo(88);
    }

    @Test
    void cadaPlanoDeveTerSeteRegrasENoveCondicoesValidasParaOArranque() {
        for (String plano : PLANOS) {
            assertThat(linhas("SELECT td.codigo || ':' || r.operador || ':' || r.substitui_sequenciamento || ':' || r.ativo || ':' "
                    + "|| r.descricao || ':' || (SELECT string_agg(c.avaliador || c.parametros::text || c.grupo || c.ativo, ';' ORDER BY c.id) "
                    + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = ?" + SEM_AS_DO_MUNICIPIO + " ORDER BY r.id", plano))
                    .as(plano).containsExactly(
                            "ELABORACAO:AND:false:true:Elaboração é única:"
                                    + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"ELABORACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true",
                            "REVISAO:AND:false:true:Requer plano em vigor; só 1 Revisão em curso:INSTRUMENTO_EM_VIGOR{}1true;"
                                    + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"REVISAO\", \"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true",
                            "ALTERACAO:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "ALTERACAO_SIMPLIFICADA_ART123:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "ALTERACAO_ADAPTACAO:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "CORRECAO_MATERIAL:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "REVOGACAO:AND:false:true:Requer plano em vigor; Revogação é única:INSTRUMENTO_EM_VIGOR{}1true;"
                                    + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"REVOGACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true");
        }
        assertThat(contar("SELECT count(*) FROM regra r JOIN instrumento_dinamica_config idc "
                + "ON idc.id = r.instrumento_dinamica_config_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo IN ('PDIM', 'PUIM', 'PPIM')" + SEM_AS_DO_MUNICIPIO)).isEqualTo(21);
        assertThat(contar("SELECT count(*) FROM condicao c JOIN regra r ON r.id = c.regra_id JOIN instrumento_dinamica_config idc "
                + "ON idc.id = r.instrumento_dinamica_config_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo IN ('PDIM', 'PUIM', 'PPIM')" + SEM_AS_DO_MUNICIPIO)).isEqualTo(27);
        assertThatCode(() -> avaliadorRegistoValidator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void aCamaraVeOsTresTiposECriaUmPdimEACcdrRecebe403() throws Exception {
        Sessao camara = camaraDeSintra();
        Sessao ccdr = ccdrLvt();

        mockMvc.perform(get("/api/v1/tipos-instrumento").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(hasItems("PDIM", "PUIM", "PPIM")));

        int instrumentosAntes = contar("SELECT count(*) FROM instrumento");
        mockMvc.perform(post(URL_INSTRUMENTOS).with(ccdr.jwt()).contentType("application/json").content(pedidoDePdim()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes);

        criarPdm(camara);
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes + 1);
    }

    @Test
    void umPdimNovoSoOfereceAElaboracaoEEmVigorOfereceAsOutrasMenosAElaboracaoEUmaRevisaoEmCursoNaoSeRepete() throws Exception {
        Sessao camara = camaraDeSintra();
        UUID pdim = criarPdm(camara);
        String disponivel = URL_INSTRUMENTOS + "/" + pdim + "/disponivel";

        mockMvc.perform(get(disponivel).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("ELABORACAO")));

        transacao.executeWithoutResult(status -> {
            Dinamica elaboracao = dinamicaRepository.saveAndFlush(new Dinamica(instrumentoRepository.findById(pdim).orElseThrow(),
                    dinamicaConfigRepository.findById(dinamicaConfigId("PDIM", "ELABORACAO")).orElseThrow(),
                    "Elaboração do PDIM", utilizadorRepository.findById(camara.utilizadorId()).orElseThrow()));
            elaboracao.setEstado(EstadoDinamica.TERMINADA);
            elaboracao.setDataConclusao(Instant.parse("2026-11-11T10:00:00Z"));
        });
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", pdim);

        mockMvc.perform(get(disponivel).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("REVISAO", "ALTERACAO", "MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS",
                        "ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL", "REVOGACAO")));

        String revisao = objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId("PDIM", "REVISAO"),
                "descritivo", "Revisão do PDIM"));
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pdim + "/dinamicas").with(camara.jwt())
                        .contentType("application/json").content(revisao))
                .andExpect(status().isCreated());

        mockMvc.perform(get(disponivel).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder("ALTERACAO", "MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS",
                        "ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL", "REVOGACAO")));
    }

    @Test
    void umPdmEmVigorNaoOfereceAAlteracaoReraeEIniciaLaPelaConfiguracaoAntigaDa404() throws Exception {
        Sessao camara = camaraDeSintra();
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(camara.jwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PDM"),
                                "designacao", "PDM de Sintra " + UUID.randomUUID(), "concelhos", List.of("1111")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID pdm = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", pdm);
        int dinamicasAntes = contar("SELECT count(*) FROM dinamica");

        mockMvc.perform(get(URL_INSTRUMENTOS + "/" + pdm + "/disponivel").with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(hasItems("ALTERACAO", "ALTERACAO_ADAPTACAO")))
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("ALTERACAO_RERAE"))));
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pdm + "/dinamicas").with(camara.jwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", 104,
                                "descritivo", "Alteração RERAE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Configuração de Dinâmica não encontrada."));
        assertThat(contar("SELECT count(*) FROM dinamica")).isEqualTo(dinamicasAntes);
    }

    private static final String DOS_PLANOS = "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo IN ('PDIM', 'PUIM', 'PPIM')"
            + SEM_AS_DO_MUNICIPIO + SEM_OS_ATOS_PONTUAIS;

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-intermunicipal.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade)), utilizadorId);
    }

    private Sessao camaraDeSintra() {
        return sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
    }

    private Sessao ccdrLvt() {
        return sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
    }

    private String pedidoDePdim() throws Exception {
        return objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PDIM"),
                "designacao", "PDIM de Sintra e Cascais " + UUID.randomUUID(), "concelhos", CONCELHOS_SINTRA_E_CASCAIS));
    }

    private UUID criarPdm(Sessao quem) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json")
                        .content(pedidoDePdim()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private List<String> procedimentos(String plano) {
        return linhas("SELECT td.codigo || ':' || ipc.ordem || ':' || tp.codigo || ':' || ipc.serie_dr "
                + "FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ti.codigo = ?" + SEM_AS_DO_MUNICIPIO
                + SEM_OS_ATOS_PONTUAIS + " ORDER BY idc.id, ipc.ordem", plano);
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private List<String> linhas(String consulta, Object... argumentos) {
        return jdbcTemplate.queryForList(consulta, String.class, argumentos);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
