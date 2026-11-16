package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.DinamicaService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O seed das Medidas Preventivas, Normas Provisórias e Suspensão do município contra o PostgreSQL migrado pelo
 * {@code master.xml}: a configuração semeada e o que o motor de regras e o sequenciamento fazem dela pelos endpoints.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedMpNpSuspensaoMunicipioPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final List<String> TIPOS = List.of("PDIM", "PDM", "PP", "PPIM", "PU", "PUIM");
    private static final List<String> TRES_DINAMICAS = List.of("MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS", "SUSPENSAO");
    private static final String DAS_TRES_DINAMICAS = "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO')";

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
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private DinamicaService dinamicaService;

    @Test
    void cadaUmDosSeisTiposDeveTerAsTresDinamicasAtivasComAPublicacaoObrigatoriaEAProrrogacao() {
        for (String tipo : TIPOS) {
            assertThat(linhas("SELECT td.codigo || ':' || idc.ativo || ':' || idc.associacao_instrumentos "
                    + "FROM instrumento_dinamica_config idc JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = ? "
                    + "AND td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO') ORDER BY td.codigo", tipo))
                    .as(tipo).containsExactly("MEDIDAS_PREVENTIVAS:true:OPCIONAL", "NORMAS_PROVISORIAS:true:UM", "SUSPENSAO:true:UM");
            assertThat(linhas("SELECT td.codigo || ':' || ipc.ordem || ':' || tp.codigo || ':' || ipc.obrigatorio || ':' "
                    + "|| ipc.serie_dr || ':' || ipc.ativo || ':' || ipc.repetivel || ':' || ipc.ato_pontual || ':' "
                    + "|| ipc.tamanho_maximo_processo FROM instrumento_procedimento_config ipc "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id " + DAS_TRES_DINAMICAS
                    + " AND ti.codigo = ? ORDER BY td.codigo, ipc.ordem", tipo))
                    .as(tipo).containsExactly(
                            "MEDIDAS_PREVENTIVAS:1:PUBLICACAO_MP:true:SEGUNDA_H:true:false:false:5368709120",
                            "MEDIDAS_PREVENTIVAS:2:PRORROGACAO_MP:false:SEGUNDA_H:true:false:false:5368709120",
                            "NORMAS_PROVISORIAS:1:PUBLICACAO_NP:true:SEGUNDA_H:true:false:false:5368709120",
                            "NORMAS_PROVISORIAS:2:PRORROGACAO_NP:false:SEGUNDA_H:true:false:false:5368709120",
                            "SUSPENSAO:1:PUBLICACAO_SUSPENSAO:true:SEGUNDA_H:true:false:false:5368709120",
                            "SUSPENSAO:2:PRORROGACAO_SUSPENSAO:false:SEGUNDA_H:true:false:false:5368709120");
        }
        assertThat(linhas("SELECT ti.codigo || ':' || td.codigo || ':' || count(*) FROM instrumento_procedimento_config ipc "
                + DAS_TRES_DINAMICAS + " GROUP BY ti.codigo, td.codigo")).hasSize(18).allSatisfy(linha -> assertThat(linha).endsWith(":2"));
    }

    @Test
    void cadaProcedimentoDeveTerACamaraEADgtAtivas() {
        assertThat(linhas("SELECT tp.codigo || ':' || string_agg(pa.tipo_entidade || '/' || pa.ativo, ',' ORDER BY pa.tipo_entidade) "
                + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id " + DAS_TRES_DINAMICAS
                + " GROUP BY ipc.id, tp.codigo ORDER BY ipc.id"))
                .hasSize(36)
                .allSatisfy(linha -> assertThat(linha).endsWith(":CM/true,DGT/true"));
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id " + DAS_TRES_DINAMICAS)).isEqualTo(72);
    }

    @Test
    void cadaTipoDeveTerTresRegrasEQuatroCondicoesValidasParaOArranque() {
        for (String tipo : TIPOS) {
            assertThat(linhas("SELECT td.codigo || ':' || r.operador || ':' || r.substitui_sequenciamento || ':' || r.ativo || ':' "
                    + "|| r.descricao || ':' || (SELECT string_agg(c.avaliador || c.parametros::text || c.grupo || c.ativo, ';' ORDER BY c.id) "
                    + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = ? AND td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO') "
                    + "AND r.instrumento_procedimento_config_id IS NULL ORDER BY td.codigo", tipo))
                    .as(tipo).containsExactly(
                            "MEDIDAS_PREVENTIVAS:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "NORMAS_PROVISORIAS:AND:false:true:Requer plano em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                            "SUSPENSAO:AND:false:true:Requer plano em vigor e Medidas Preventivas publicadas no mesmo plano:"
                                    + "INSTRUMENTO_EM_VIGOR{}1true;OCORRENCIA_EM_ESTADO{\"codigo\": \"PUBLICACAO_MP\", "
                                    + "\"estado\": \"PUBLICADO\", \"tipo_ocorrencia\": \"PROCESSO\"}1true");
        }
        String dasRegras = "FROM regra r JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO')";
        assertThat(contar("SELECT count(*) " + dasRegras)).isEqualTo(18);
        assertThat(contar("SELECT count(*) FROM condicao c WHERE c.regra_id IN (SELECT r.id " + dasRegras + ")")).isEqualTo(24);
        assertThat(contar("SELECT count(*) FROM regra r JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = r.instrumento_procedimento_config_id " + DAS_TRES_DINAMICAS)).isZero();
        assertThatCode(() -> avaliadorRegistoValidator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void umPdmEmVigorSemMpPublicadaOfereceMpENpENaoASuspensaoQueApareceComAPublicacaoDasMp() throws Exception {
        Sessao camara = camaraDeSintra();
        Instrumento pdm = novoInstrumento("PDM", true);

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(hasItems("MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS")))
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("SUSPENSAO"))));

        UUID medidas = iniciar(pdm, "MEDIDAS_PREVENTIVAS", camara);
        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[?(@.tipoDinamica == 'MEDIDAS_PREVENTIVAS')].procedimentosDisponiveis[*].tipoProcedimento")
                        .value(contains("PUBLICACAO_MP")))
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(not(hasItem("SUSPENSAO"))));

        mockMvc.perform(post("/api/v1/dinamicas/" + medidas + "/processos").with(camara.jwt()).contentType("application/json")
                        .content(pedidoDeProcesso("PDM", "MEDIDAS_PREVENTIVAS", "PRORROGACAO_MP")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEQUENCIAMENTO_NAO_CUMPRIDO"));

        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + medidas + "/processos").with(camara.jwt())
                        .contentType("application/json").content(pedidoDeProcesso("PDM", "MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID publicacao = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        jdbcTemplate.update("UPDATE processo SET estado = 'PUBLICADO' WHERE id = ?", publicacao);
        transacao.executeWithoutResult(status ->
                dinamicaService.verificarConclusaoPeloSistema(dinamicaRepository.findById(medidas).orElseThrow()));
        assertThat(dinamicaRepository.findById(medidas).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(instrumentoRepository.findById(pdm.getId()).orElseThrow().isEmVigor()).isTrue();

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(hasItems("MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS", "SUSPENSAO")));
        iniciar(pdm, "SUSPENSAO", camara);
    }

    @Test
    void aPublicacaoDasMpRegistadaExternamenteTambemDesbloqueiaASuspensao() throws Exception {
        Sessao camara = camaraDeSintra();
        Instrumento pdm = novoInstrumento("PDM", true);
        UUID medidas = iniciar(pdm, "MEDIDAS_PREVENTIVAS", camara);

        long entidade = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, CODIGO_CAMARA_DE_SINTRA);
        mockMvc.perform(post("/api/v1/dinamicas/" + medidas + "/processos/registo-externo").with(master())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", procedimentoConfigId("PDM", "MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP"),
                                "entidadeId", entidade, "numeroDr", "123/2026", "dataPublicacaoDr", "2026-11-10",
                                "motivo", "Publicação anterior ao SAIGT"))))
                .andExpect(status().isCreated());

        assertThat(dinamicaRepository.findById(medidas).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(hasItem("SUSPENSAO")));
    }

    @Test
    void umPdmQueNaoEstaEmVigorNaoOfereceNenhumaDasTresERecusaASuspensaoCom409() throws Exception {
        Sessao camara = camaraDeSintra();
        Instrumento pdm = novoInstrumento("PDM", false);

        mockMvc.perform(get(disponivel(pdm)).with(camara.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(everyItem(not(in(TRES_DINAMICAS)))));
        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pdm.getId() + "/dinamicas").with(camara.jwt())
                        .contentType("application/json").content(pedidoDeDinamica("PDM", "SUSPENSAO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
    }

    @Test
    void duasDinamicasDeMedidasPreventivasPodemCorrerEmParaleloNoMesmoPlano() throws Exception {
        Sessao camara = camaraDeSintra();
        Instrumento pdm = novoInstrumento("PDM", true);
        UUID primeira = iniciar(pdm, "MEDIDAS_PREVENTIVAS", camara);
        UUID segunda = iniciar(pdm, "MEDIDAS_PREVENTIVAS", camara);

        assertThat(dinamicaRepository.findByInstrumento_IdAndEstado(pdm.getId(), EstadoDinamica.EM_CURSO))
                .extracting(Dinamica::getId).containsExactlyInAnyOrder(primeira, segunda);
    }

    @Test
    void umTipoDeEntidadeSemPermissaoRecebe403() throws Exception {
        Instrumento pu = novoInstrumento("PU", true);

        mockMvc.perform(post(URL_INSTRUMENTOS + "/" + pu.getId() + "/dinamicas").with(ccdrLvt().jwt())
                        .contentType("application/json").content(pedidoDeDinamica("PU", "MEDIDAS_PREVENTIVAS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-mp.pt");
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

    private RequestPostProcessor master() {
        Utilizador master = new Utilizador("Master MP", "master-" + UUID.randomUUID() + "@dgt.pt", "AD_DGT");
        master.setPerfilInterno(PerfilInterno.MASTER);
        String sub = utilizadorRepository.saveAndFlush(master).getId().toString();
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private Instrumento novoInstrumento(String tipo, boolean emVigor) {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável MP", "responsavel-" + UUID.randomUUID() + "@teste-mp.pt", "INCM"));
        String numero = tipo + "-MP-" + UUID.randomUUID().toString().substring(0, 8);
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> tipo.equals(t.getCodigo())).findFirst().orElseThrow(), numero, "Plano de teste " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(emVigor);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID iniciar(Instrumento instrumento, String tipoDinamica, Sessao quem) throws Exception {
        String tipo = jdbcTemplate.queryForObject("SELECT ti.codigo FROM instrumento i JOIN tipo_instrumento ti "
                + "ON ti.id = i.tipo_instrumento_id WHERE i.id = ?", String.class, instrumento.getId());
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/" + instrumento.getId() + "/dinamicas").with(quem.jwt())
                        .contentType("application/json").content(pedidoDeDinamica(tipo, tipoDinamica)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private String pedidoDeDinamica(String tipoInstrumento, String tipoDinamica) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoDinamicaConfigId", dinamicaConfigId(tipoInstrumento, tipoDinamica),
                "descritivo", tipoDinamica + " de teste"));
    }

    private String pedidoDeProcesso(String tipoInstrumento, String tipoDinamica, String tipoProcedimento) throws Exception {
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        return objectMapper.writeValueAsString(Map.of(
                "instrumentoProcedimentoConfigId", procedimentoConfigId(tipoInstrumento, tipoDinamica, tipoProcedimento),
                "tipoAtoId", tipoAto));
    }

    private static String disponivel(Instrumento instrumento) {
        return URL_INSTRUMENTOS + "/" + instrumento.getId() + "/disponivel";
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

    private List<String> linhas(String consulta, Object... argumentos) {
        return jdbcTemplate.queryForList(consulta, String.class, argumentos);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
