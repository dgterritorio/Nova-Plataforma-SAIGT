package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobre o seed Liquibase (a Alteração de PDM e a sua Decisão de início), que os testes em H2 não aplicam. A base é
 * partilhada pelos testes da classe: a Dinâmica e o processo da Alteração de PDM criam-se uma só vez.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ConfiguracaoEstruturaSeedPostgresIntegrationTest {

    private static final String DINAMICAS = "/api/v1/configuracao/dinamicas-config";
    private static final String PROCEDIMENTOS = "/api/v1/configuracao/procedimentos-config";
    private static final int PORTA_POSTGRES = 5432;

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
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private InstrumentoRepository instrumentoRepository;
    @Autowired
    private DinamicaRepository dinamicaRepository;
    @Autowired
    private ProcessoRepository processoRepository;

    private UUID masterId;

    /** A auditoria tem FK para `utilizador` nesta base (o esquema do Hibernate não a tem): o MASTER tem de existir. */
    private RequestPostProcessor comoMaster() {
        if (masterId == null) {
            masterId = utilizadorRepository.findAll().stream().filter(u -> "master-cfg@dgt.pt".equals(u.getEmail()))
                    .findFirst().map(Utilizador::getId)
                    .orElseGet(() -> {
                        Utilizador master = new Utilizador("Master Configuração", "master-cfg@dgt.pt", "AD_DGT");
                        master.setPerfilInterno(PerfilInterno.MASTER);
                        return utilizadorRepository.saveAndFlush(master).getId();
                    });
        }
        String sub = masterId.toString();
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private long idTipoInstrumento(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long idTipoDinamica(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_dinamica WHERE codigo = ?", Long.class, codigo);
    }

    private long alteracaoDePdm() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_instrumento ti ON ti.id = c.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = c.tipo_dinamica_id "
                + "WHERE ti.codigo = 'PDM' AND td.codigo = 'ALTERACAO'", Long.class);
    }

    private long decisaoInicioDaAlteracaoDePdm() {
        return jdbcTemplate.queryForObject("SELECT p.id FROM instrumento_procedimento_config p "
                + "JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id "
                + "WHERE p.instrumento_dinamica_config_id = ? AND tp.codigo = 'DECISAO_INICIO'", Long.class, alteracaoDePdm());
    }

    /** Uma Dinâmica da Alteração de PDM com um processo da Decisão de início, criados uma só vez na base partilhada. */
    private synchronized void garantirDinamicaEProcessoNaAlteracaoDePdm() {
        long configId = alteracaoDePdm();
        long decisaoId = decisaoInicioDaAlteracaoDePdm();
        if (!processoRepository.configsComProcessos(List.of(decisaoId)).isEmpty()) {
            return;
        }
        transacao.executeWithoutResult(estado -> {
            InstrumentoDinamicaConfig config = dinamicaConfigRepository.findById(configId).orElseThrow();
            Utilizador autor = utilizadorRepository.saveAndFlush(
                    new Utilizador("Autora PDM", "autora-pdm@cm-config.pt", "INCM"));
            Instrumento instrumento = instrumentoRepository.saveAndFlush(
                    new Instrumento(config.getTipoInstrumento(), "PDM-CFG", "PDM de Teste", autor, autor));
            Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, config, "Alteração do PDM", autor));
            processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfigRepository.findById(decisaoId).orElseThrow(),
                    autor, entidadeRepository.saveAndFlush(EntidadeTeste.nova("509999001", "Câmara Municipal de Teste"))));
        });
    }

    private static Map<String, Object> corpoDinamica(long tipoInstrumentoId, long tipoDinamicaId, String associacao) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("tipoInstrumentoId", tipoInstrumentoId);
        corpo.put("tipoDinamicaId", tipoDinamicaId);
        corpo.put("associacaoInstrumentos", associacao);
        corpo.put("ordem", 1);
        corpo.put("ativo", true);
        return corpo;
    }

    private Map<String, Object> corpoDaDecisao() {
        Map<String, Object> linha = jdbcTemplate.queryForMap("SELECT instrumento_dinamica_config_id, tipo_procedimento_id, "
                + "ordem, obrigatorio, serie_dr, tamanho_maximo_processo, repetivel, ato_pontual, ativo "
                + "FROM instrumento_procedimento_config WHERE id = ?", decisaoInicioDaAlteracaoDePdm());
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoDinamicaConfigId", linha.get("instrumento_dinamica_config_id"));
        corpo.put("tipoProcedimentoId", linha.get("tipo_procedimento_id"));
        corpo.put("ordem", linha.get("ordem"));
        corpo.put("obrigatorio", linha.get("obrigatorio"));
        corpo.put("serieDr", linha.get("serie_dr"));
        corpo.put("tamanhoMaximoProcesso", linha.get("tamanho_maximo_processo"));
        corpo.put("repetivel", linha.get("repetivel"));
        corpo.put("atoPontual", linha.get("ato_pontual"));
        corpo.put("ativo", linha.get("ativo"));
        return corpo;
    }

    @Test
    void oParSemeadoPdmAlteracaoDeveDar400NoTipoDinamica() throws Exception {
        mockMvc.perform(post(DINAMICAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoDinamica(idTipoInstrumento("PDM"), idTipoDinamica("ALTERACAO"), "UM"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoDinamicaId"));
    }

    @Test
    void oParLivrePnpotElaboracaoDeveSerCriadoEAparecerNosWorkflows() throws Exception {
        String resposta = mockMvc.perform(post(DINAMICAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoDinamica(idTipoInstrumento("PNPOT"), idTipoDinamica("ELABORACAO"), "UM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emUso").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get("/api/v1/workflows").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].titulo").value("PNPOT — Elaboração"));
    }

    @Test
    void aAlteracaoDePdmComDinamicasNaoMudaDeTipoMasMudaAAssociacao() throws Exception {
        garantirDinamicaEProcessoNaAlteracaoDePdm();
        long id = alteracaoDePdm();
        TipoDinamica outra = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("CFG_OUTRA_DINAMICA", "Outra"));

        mockMvc.perform(put(DINAMICAS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoDinamica(idTipoInstrumento("PDM"), outra.getId(), "UM"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));
        assertThat(jdbcTemplate.queryForObject("SELECT tipo_dinamica_id FROM instrumento_dinamica_config WHERE id = ?",
                Long.class, id)).isEqualTo(idTipoDinamica("ALTERACAO"));

        mockMvc.perform(put(DINAMICAS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoDinamica(idTipoInstrumento("PDM"), idTipoDinamica("ALTERACAO"), "UM_OU_MAIS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associacaoInstrumentos").value("UM_OU_MAIS"))
                .andExpect(jsonPath("$.emUso").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT associacao_instrumentos FROM instrumento_dinamica_config WHERE id = ?",
                String.class, id)).isEqualTo("UM_OU_MAIS");
    }

    @Test
    void aDecisaoDeInicioComProcessosNaoMudaDeTipoMasMudaOsRestantesCampos() throws Exception {
        garantirDinamicaEProcessoNaAlteracaoDePdm();
        long id = decisaoInicioDaAlteracaoDePdm();
        TipoProcedimento outro = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CFG_OUTRO_PROCEDIMENTO", "Outro"));

        Map<String, Object> mudaTipo = corpoDaDecisao();
        mudaTipo.put("tipoProcedimentoId", outro.getId());
        mockMvc.perform(put(PROCEDIMENTOS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mudaTipo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));

        Map<String, Object> mudaCampos = corpoDaDecisao();
        boolean obrigatorio = (Boolean) mudaCampos.get("obrigatorio");
        mudaCampos.put("obrigatorio", !obrigatorio);
        mudaCampos.put("repetivel", true);
        mudaCampos.put("tamanhoMaximoProcesso", 1_073_741_824L);
        mockMvc.perform(put(PROCEDIMENTOS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mudaCampos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoProcedimento.codigo").value("DECISAO_INICIO"))
                .andExpect(jsonPath("$.emUso").value(true))
                .andExpect(jsonPath("$.repetivel").value(true));

        Map<String, Object> linha = jdbcTemplate.queryForMap("SELECT obrigatorio, repetivel, tamanho_maximo_processo, "
                + "tipo_procedimento_id FROM instrumento_procedimento_config WHERE id = ?", id);
        assertThat(linha.get("obrigatorio")).isEqualTo(!obrigatorio);
        assertThat(linha.get("repetivel")).isEqualTo(true);
        assertThat(((Number) linha.get("tamanho_maximo_processo")).longValue()).isEqualTo(1_073_741_824L);
        assertThat(((Number) linha.get("tipo_procedimento_id")).longValue()).isNotEqualTo(outro.getId());
    }

    @Test
    void aListaDaAlteracaoDePdmVemPorOrdemComOEmUsoSoNaDecisaoDeInicio() throws Exception {
        garantirDinamicaEProcessoNaAlteracaoDePdm();
        long configId = alteracaoDePdm();
        int total = jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento_procedimento_config "
                + "WHERE instrumento_dinamica_config_id = ?", Integer.class, configId);

        String pagina = mockMvc.perform(get(PROCEDIMENTOS).param("instrumentoDinamicaConfigId", String.valueOf(configId))
                        .param("size", "100").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(total))
                .andReturn().getResponse().getContentAsString();
        JsonNode conteudo = objectMapper.readTree(pagina).get("content");
        int ordemAnterior = 0;
        for (JsonNode linha : conteudo) {
            assertThat(linha.get("ordem").asInt()).isGreaterThanOrEqualTo(ordemAnterior);
            ordemAnterior = linha.get("ordem").asInt();
            assertThat(linha.get("emUso").asBoolean())
                    .isEqualTo("DECISAO_INICIO".equals(linha.get("tipoProcedimento").get("codigo").asText()));
            assertThat(linha.has("prazoDias")).isFalse();
        }
        assertThat(conteudo.size()).isEqualTo(total).isGreaterThan(1);
    }

    @Test
    void osMetodosNovosDosRepositoriosDevolvemOEsperadoSobreOSeed() {
        garantirDinamicaEProcessoNaAlteracaoDePdm();
        long pdm = idTipoInstrumento("PDM");
        long alteracao = alteracaoDePdm();
        long decisao = decisaoInicioDaAlteracaoDePdm();
        List<Long> configsDoPdm = jdbcTemplate.queryForList("SELECT c.id FROM instrumento_dinamica_config c "
                + "WHERE c.tipo_instrumento_id = ?", Long.class, pdm);
        List<Long> procedimentosDaAlteracao = jdbcTemplate.queryForList("SELECT id FROM instrumento_procedimento_config "
                + "WHERE instrumento_dinamica_config_id = ?", Long.class, alteracao);

        transacao.executeWithoutResult(estado -> {
            List<InstrumentoDinamicaConfig> doPdm = dinamicaConfigRepository.pesquisar(pdm, null, PageRequest.of(0, 100))
                    .getContent();
            assertThat(doPdm).extracting(InstrumentoDinamicaConfig::getId).containsExactlyInAnyOrderElementsOf(configsDoPdm);
            assertThat(doPdm).extracting(c -> c.getTipoDinamica().getId()).isSorted();
            assertThat(procedimentoConfigRepository.pesquisar(alteracao, null, PageRequest.of(0, 100)).getContent())
                    .extracting(p -> p.getOrdem()).isSorted().hasSize(procedimentosDaAlteracao.size());
        });
        assertThat(dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_Id(pdm, idTipoDinamica("ALTERACAO")))
                .isTrue();
        assertThat(dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(pdm,
                idTipoDinamica("ALTERACAO"), alteracao)).isFalse();
        assertThat(procedimentoConfigRepository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_IdAndIdNot(alteracao,
                jdbcTemplate.queryForObject("SELECT id FROM tipo_procedimento WHERE codigo = 'DECISAO_INICIO'", Long.class),
                decisao)).isFalse();
        assertThat(dinamicaRepository.configsComDinamicas(configsDoPdm)).containsExactly(alteracao);
        assertThat(processoRepository.configsComProcessos(procedimentosDaAlteracao)).containsExactly(decisao);
    }
}
