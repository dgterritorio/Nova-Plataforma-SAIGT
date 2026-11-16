package com.conkord.saigt.db;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O motivo da alteração das Dinâmicas do município contra o PostgreSQL migrado pelo {@code master.xml}: o que o seed
 * de `pede_motivo_alteracao` faz à gravação e à submissão do primeiro Procedimento, e o filtro da listagem por motivo.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedMotivoAlteracaoDinamicasPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String LEI_DE_TESTE = "LEI-TESTE";
    private static final String PROGRAMA_DE_TESTE = "PNEC";
    private static final String TIPOS_DE_MOTIVO = "('MOTIVO_ALTERACAO_LEI', 'MOTIVO_ALTERACAO_PROGRAMA', 'MOTIVO_ALTERACAO_PLANO')";
    private static final String TODA_A_AREA = "\"localizacao\":{\"aplicavelTodaArea\":true}";
    private static final String COM_A_LEI = TODA_A_AREA + ",\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\""
            + LEI_DE_TESTE + "\"}]";
    private static final String COM_O_PROGRAMA = TODA_A_AREA + ",\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_PROGRAMA\","
            + "\"codigo\":\"" + PROGRAMA_DE_TESTE + "\"}]";
    private static final String FILTRO_DO_PROGRAMA = "MOTIVO_ALTERACAO_PROGRAMA:" + PROGRAMA_DE_TESTE;

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
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private record Cenario(UUID processoId, RequestPostProcessor autor) {
    }

    @BeforeEach
    void motivosDeTeste() {
        jdbcTemplate.update("INSERT INTO lista_valores (tipo, codigo, descricao, ordem) VALUES "
                + "('MOTIVO_ALTERACAO_LEI', ?, 'Lei de teste', 99), ('MOTIVO_ALTERACAO_PROGRAMA', ?, 'Programa de teste', 99)",
                LEI_DE_TESTE, PROGRAMA_DE_TESTE);
    }

    @AfterEach
    void reporListas() {
        jdbcTemplate.update("DELETE FROM lista_valores WHERE codigo IN (?, ?) AND tipo IN " + TIPOS_DE_MOTIVO,
                LEI_DE_TESTE, PROGRAMA_DE_TESTE);
        jdbcTemplate.update("UPDATE lista_valores SET ativo = true WHERE tipo IN " + TIPOS_DE_MOTIVO);
    }

    @Test
    void aAlteracaoPorAdaptacaoDoPuExigeOMotivoComUmaLeiAtivaEPassaComALei() throws Exception {
        Cenario cenario = cenario("PU", "ALTERACAO_ADAPTACAO");
        gravar(cenario, TODA_A_AREA).andExpect(status().isOk());

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("motivosAlteracao"));

        gravar(cenario, COM_A_LEI).andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void comAsTresListasDeMotivosVaziasAAlteracaoPorAdaptacaoDoPuSubmeteSemMotivos() throws Exception {
        jdbcTemplate.update("UPDATE lista_valores SET ativo = false WHERE tipo IN " + TIPOS_DE_MOTIVO);
        Cenario cenario = cenario("PU", "ALTERACAO_ADAPTACAO");
        gravar(cenario, TODA_A_AREA).andExpect(status().isOk());

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void aAlteracaoPorAdaptacaoRecusaARespostaAPergunta() throws Exception {
        Cenario cenario = cenario("PU", "ALTERACAO_ADAPTACAO");

        detalhe(cenario).andExpect(jsonPath("$.pedeMotivoAlteracao").value("OBRIGATORIO"));
        gravar(cenario, TODA_A_AREA + ",\"conformaHierarquiaSuperior\":true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("conformaHierarquiaSuperior"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este procedimento não faz a pergunta da conformação."));
    }

    @Test
    void aPrimeiraAlteracaoSimplificadaDl10DoPpExigeARespostaAPergunta() throws Exception {
        Cenario cenario = cenario("PP", "ALTERACAO_SIMPLIFICADA_DL10_2024");
        detalhe(cenario).andExpect(jsonPath("$.pedeMotivoAlteracao").value("COM_PERGUNTA"));
        gravar(cenario, TODA_A_AREA).andExpect(status().isOk());

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("conformaHierarquiaSuperior"));

        gravar(cenario, TODA_A_AREA + ",\"conformaHierarquiaSuperior\":false").andExpect(status().isOk());
        submeter(cenario).andExpect(status().isOk());
    }

    @Test
    void oTecnicoFiltraPeloProgramaEAEntidadeVeOsSeusProcessosComoSemFiltro() throws Exception {
        Cenario comPrograma = cenario("PU", "ALTERACAO_ADAPTACAO");
        gravar(comPrograma, COM_O_PROGRAMA).andExpect(status().isOk());
        submeter(comPrograma).andExpect(status().isOk());
        Cenario comLei = cenario("PP", "ALTERACAO_ADAPTACAO");
        gravar(comLei, COM_A_LEI).andExpect(status().isOk());
        submeter(comLei).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos").param("motivoAlteracao", FILTRO_DO_PROGRAMA).with(tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(comPrograma.processoId().toString()));
        mockMvc.perform(get("/api/v1/processos").param("motivoAlteracao", FILTRO_DO_PROGRAMA)
                        .param("motivoAlteracao", "MOTIVO_ALTERACAO_LEI:" + LEI_DE_TESTE).with(tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(comPrograma.processoId().toString())))
                .andExpect(jsonPath("$.content[*].id").value(hasItem(comLei.processoId().toString())));
        mockMvc.perform(get("/api/v1/processos").param("motivoAlteracao", "MOTIVO_ALTERACAO_PLANO:" + PROGRAMA_DE_TESTE)
                        .with(tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(comPrograma.processoId().toString()))));

        long semFiltro = totalDaListagem(comLei.autor(), null);
        assertThat(semFiltro).isGreaterThanOrEqualTo(2);
        assertThat(totalDaListagem(comLei.autor(), FILTRO_DO_PROGRAMA)).isEqualTo(semFiltro);
    }

    @Test
    void umCodigoComAspasNaoParteAConsulta() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("motivoAlteracao", "MOTIVO_ALTERACAO_PROGRAMA:a'b\"c\\")
                        .with(tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void umMotivoSemTipoOuComOutroTipoDa400NoMotivoAlteracao() throws Exception {
        for (String valor : List.of(PROGRAMA_DE_TESTE, "FREGUESIA:" + PROGRAMA_DE_TESTE, "MOTIVO_ALTERACAO_PROGRAMA:")) {
            mockMvc.perform(get("/api/v1/processos").param("motivoAlteracao", valor).with(tecnico()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("motivoAlteracao"));
        }
    }

    private long totalDaListagem(RequestPostProcessor utilizador, String motivo) throws Exception {
        var pedido = get("/api/v1/processos").with(utilizador);
        if (motivo != null) {
            pedido.param("motivoAlteracao", motivo);
        }
        String corpo = mockMvc.perform(pedido).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(corpo.replaceAll(".*\"totalElements\":(\\d+).*", "$1"));
    }

    private static RequestPostProcessor tecnico() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INTERNO")
                .claim("perfil_interno", "TECNICO"));
    }

    private Cenario cenario(String tipoInstrumento, String tipoDinamica) {
        long procedimentoId = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE ti.codigo = ? AND td.codigo = ? AND ipc.ordem = 1", Long.class, tipoInstrumento, tipoDinamica);
        long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return transacao.execute(estado -> {
            Utilizador autor = utilizadorRepository.saveAndFlush(
                    new Utilizador("Autora " + sufixo, "autora-" + sufixo + "@teste-mot.pt", "INCM"));
            var procedimento = procedimentoConfigRepository.findById(procedimentoId).orElseThrow();
            var entidade = entidadeRepository.findById(entidadeId).orElseThrow();
            Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(
                    tipoInstrumentoRepository.findAll().stream().filter(t -> tipoInstrumento.equals(t.getCodigo()))
                            .findFirst().orElseThrow(),
                    tipoInstrumento + "-MOT-" + sufixo, "Plano de teste " + sufixo, autor, autor));
            Dinamica dinamica = dinamicaRepository.saveAndFlush(
                    new Dinamica(instrumento, procedimento.getInstrumentoDinamicaConfig(), tipoDinamica + " de teste", autor));
            Processo processo = new Processo(dinamica, procedimento, autor, entidade);
            processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
            processo.setSumario("Resumo do ato.");
            processo.setDataAssinatura(LocalDate.now());
            processo.setNormaLegal("Norma Legal X");
            processo.setTipoInformacao("TEXTO_INTEGRAL");
            processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
            processo.setEntidadeEmitente("Câmara Municipal de Sintra");
            UUID processoId = processoRepository.saveAndFlush(processo).getId();
            // Sem ficheiros nos testes: os itens obrigatórios levam observações para a submissão passar (FR-137).
            jdbcTemplate.update("INSERT INTO processo_documento_observacao (processo_id, documento_config_id, observacoes) "
                    + "SELECT ?, id, 'Sem ficheiro no teste.' FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = ? AND obrigatorio AND ativo", processoId, procedimentoId);
            Map<String, Object> claimEntidade = Map.of("tipo_entidade", "CM", "id", entidadeId, "nif", entidade.getNipc(),
                    "nome", "Câmara Municipal de Sintra", "pcm", false, "incm_enrollment_id", "2015482",
                    "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
            String sub = autor.getId().toString();
            return new Cenario(processoId, jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "INCM")
                    .claim("entidade", claimEntidade)));
        });
    }

    private long versao(Cenario cenario) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, cenario.processoId());
    }

    private ResultActions gravar(Cenario cenario, String camposExtra) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"entidadeEmitente\":\"Câmara Municipal de Sintra\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"],"
                + camposExtra + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processoId() + "/dados-formulario")
                .with(cenario.autor()).contentType("application/json").content(corpo));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processoId() + "/submeter")
                .with(cenario.autor()).contentType("application/json").content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processoId()).with(cenario.autor()))
                .andExpect(status().isOk());
    }
}
