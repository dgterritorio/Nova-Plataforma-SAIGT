package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As regras da REN (sem Dinâmicas em simultâneo e a precedência das três Dinâmicas: a Delimitação é a inicial e
 * única, a Alteração e a Correção exigem a REN em vigor) vêm do seed Liquibase, que os testes em H2 não aplicam: arranca a
 * aplicação num PostgreSQL real com o {@code master.xml}. As configurações da REN ficam inativas no seed e sem
 * Procedimentos nem permissões; o teste ativa-as e cria-os, como fará a configuração da REN.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class InstrumentoRenSemDinamicasEmSimultaneoPostgresIntegrationTest {

    private static final String URL = "/api/v1/instrumentos";
    private static final String NIF = "501017701";
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
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    private static Map<String, InstrumentoDinamicaConfig> configuracoes;
    private static UUID utilizadorId;

    @BeforeEach
    void ativarARenUmaVez() {
        if (configuracoes == null) {
            configuracoes = ativarAsConfiguracoesDaRenComPermissaoParaACamara();
            entidadeRepository.saveAndFlush(EntidadeTeste.nova(NIF, "Câmara Municipal REN"));
            utilizadorId = utilizadorRepository.saveAndFlush(
                    new Utilizador("Autor REN", "autor-ren@cm-ren.pt", "INCM")).getId();
        }
    }

    @Test
    void deveTerAsRegrasDePrecedenciaEASemDinamicasEmSimultaneoAtivasEmCadaConfiguracaoDaRen() {
        assertThat(jdbcTemplate.queryForList("SELECT td.codigo || ':' || count(*) FROM regra r "
                + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE ti.codigo = 'REN' AND r.ativo GROUP BY td.codigo ORDER BY td.codigo", String.class))
                .containsExactly("ALTERACAO:2", "CORRECAO_MATERIAL_RETIFICACAO:2", "DELIMITACAO:2");
    }

    @Test
    void deveOferecerSoADelimitacaoAUmaRenNovaSemDinamicas() throws Exception {
        Instrumento ren = novaRen("REN-NOVA", null, false);

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("DELIMITACAO")));
        mockMvc.perform(post(URL + "/" + ren.getId() + "/dinamicas").with(jwtDaCamara())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(
                                configuracoes.get("ALTERACAO").getId(), "Alteração de uma REN por delimitar"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
    }

    @Test
    void naoDeveOferecerNadaComADelimitacaoEmCursoEDepoisDeTerminadaOferecerAlteracaoECorrecaoSemADelimitacao() throws Exception {
        Instrumento ren = novaRen("REN-DELIM", null, false);
        UUID delimitacao = iniciar(ren, "DELIMITACAO");

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", empty()));

        terminar(delimitacao);
        transacao.executeWithoutResult(status -> instrumentoRepository.findById(ren.getId()).orElseThrow().setEmVigor(true));

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(containsInAnyOrder("ALTERACAO", "CORRECAO_MATERIAL_RETIFICACAO")));
        mockMvc.perform(post(URL + "/" + ren.getId() + "/dinamicas").with(jwtDaCamara())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(
                                configuracoes.get("DELIMITACAO").getId(), "Segunda Delimitação"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
    }

    @Test
    void naoDeveOferecerADelimitacaoAUmaRenDoSnitEmVigor() throws Exception {
        Instrumento ren = novaRen("REN-SNIT", "IGT-SNIT-REN-232", true);

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(containsInAnyOrder("ALTERACAO", "CORRECAO_MATERIAL_RETIFICACAO")));
    }

    @Test
    void naoDeveOferecerNemDeixarIniciarDinamicasDaRenComUmaEmCursoEVoltarAOferecerDepoisDeTerminada() throws Exception {
        Instrumento ren = novaRen("REN-1", null, false);
        terminar(iniciar(ren, "DELIMITACAO"));
        transacao.executeWithoutResult(status -> instrumentoRepository.findById(ren.getId()).orElseThrow().setEmVigor(true));

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(containsInAnyOrder("ALTERACAO", "CORRECAO_MATERIAL_RETIFICACAO")));

        UUID alteracao = iniciar(ren, "ALTERACAO");

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", empty()));
        CriarDinamicaRequest correcao = new CriarDinamicaRequest(
                configuracoes.get("CORRECAO_MATERIAL_RETIFICACAO").getId(), "Correção da REN");
        mockMvc.perform(post(URL + "/" + ren.getId() + "/dinamicas").with(jwtDaCamara())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(correcao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));
        assertThat(dinamicaRepository.findByInstrumento_IdAndEstado(ren.getId(), EstadoDinamica.EM_CURSO))
                .extracting(Dinamica::getId).containsExactly(alteracao);

        terminar(alteracao);

        mockMvc.perform(get(disponivel(ren)).with(jwtDaCamara()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica")
                        .value(containsInAnyOrder("ALTERACAO", "CORRECAO_MATERIAL_RETIFICACAO")));
    }

    private Instrumento novaRen(String numero, String idIgtSnit, boolean emVigor) {
        TipoInstrumento tipoRen = tipoInstrumentoRepository.findAll().stream()
                .filter(tipo -> "REN".equals(tipo.getCodigo())).findFirst().orElseThrow();
        Utilizador utilizador = utilizadorRepository.findById(utilizadorId).orElseThrow();
        Instrumento ren = new Instrumento(tipoRen, numero, "REN de Teste " + numero, utilizador, utilizador);
        ren.setIdIgtSnit(idIgtSnit);
        ren.setEmVigor(emVigor);
        return instrumentoRepository.saveAndFlush(ren);
    }

    private UUID iniciar(Instrumento ren, String tipoDinamica) {
        return transacao.execute(status -> dinamicaRepository.saveAndFlush(new Dinamica(
                instrumentoRepository.findById(ren.getId()).orElseThrow(), configuracoes.get(tipoDinamica),
                tipoDinamica + " da REN", utilizadorRepository.findById(utilizadorId).orElseThrow())).getId());
    }

    private void terminar(UUID dinamicaId) {
        transacao.executeWithoutResult(status -> {
            Dinamica dinamica = dinamicaRepository.findById(dinamicaId).orElseThrow();
            dinamica.setEstado(EstadoDinamica.TERMINADA);
            dinamica.setDataConclusao(Instant.parse("2026-11-11T10:00:00Z"));
        });
    }

    private static String disponivel(Instrumento ren) {
        return URL + "/" + ren.getId() + "/disponivel";
    }

    private Map<String, InstrumentoDinamicaConfig> ativarAsConfiguracoesDaRenComPermissaoParaACamara() {
        jdbcTemplate.update("UPDATE instrumento_dinamica_config SET ativo = true "
                + "WHERE tipo_instrumento_id = (SELECT id FROM tipo_instrumento WHERE codigo = 'REN')");
        Map<String, InstrumentoDinamicaConfig> ativadas = new java.util.HashMap<>();
        for (String tipoDinamica : List.of("DELIMITACAO", "ALTERACAO", "CORRECAO_MATERIAL_RETIFICACAO")) {
            InstrumentoDinamicaConfig config = transacao.execute(status -> dinamicaConfigRepository.findAll().stream()
                    .filter(c -> "REN".equals(c.getTipoInstrumento().getCodigo())
                            && tipoDinamica.equals(c.getTipoDinamica().getCodigo()))
                    .findFirst().orElseThrow());
            // O seed dá a cada Dinâmica da REN o Procedimento PUBLICACAO_REN (CCDR e DGT); a Câmara ganha-o aqui.
            InstrumentoProcedimentoConfig procedimento = transacao.execute(status -> procedimentoConfigRepository.findAll().stream()
                    .filter(p -> p.getInstrumentoDinamicaConfig().getId().equals(config.getId()))
                    .findFirst().orElseThrow());
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
            ativadas.put(tipoDinamica, config);
        }
        return ativadas;
    }

    private RequestPostProcessor jwtDaCamara() {
        Entidade entidade = entidadeRepository.findAllByNipc(NIF).get(0);
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("id", entidade.getId(), "tipo_entidade", "CM", "nif", NIF,
                        "nome", "Câmara Municipal REN", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }
}
