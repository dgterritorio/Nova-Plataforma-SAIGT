package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
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
import org.springframework.data.domain.Sort;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobre o seed Liquibase (matriz de permissões, 17 tipos de ato, 8 redes), que os testes em H2 não aplicam. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class ConfiguracaoPermissoesCatalogosSeedPostgresIntegrationTest {

    private static final String PERMISSOES = "/api/v1/configuracao/permissoes";
    private static final String REDES = "/api/v1/configuracao/redes-defesa";
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
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private PermissaoAcaoRepository permissaoAcaoRepository;
    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired
    private TipoAtoRepository tipoAtoRepository;
    @Autowired
    private SgifrRedeDefesaRepository redeDefesaRepository;
    @Autowired
    private ProcessoRepository processoRepository;

    private UUID masterId;

    /** A auditoria tem FK para `utilizador` nesta base: o MASTER tem de existir, com `perfil_interno`. */
    private RequestPostProcessor comoMaster() {
        if (masterId == null) {
            masterId = utilizadorRepository.findAll().stream().filter(u -> "master-cat@dgt.pt".equals(u.getEmail()))
                    .findFirst().map(Utilizador::getId)
                    .orElseGet(() -> {
                        Utilizador master = new Utilizador("Master Catálogos", "master-cat@dgt.pt", "AD_DGT");
                        master.setPerfilInterno(PerfilInterno.MASTER);
                        return utilizadorRepository.saveAndFlush(master).getId();
                    });
        }
        String sub = masterId.toString();
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private static RequestPostProcessor daCcdr() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CCDR_DR", "nif", "517622610", "nome", "CCDR de Teste", "pcm", false)));
    }

    private long alteracaoDePu() {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_instrumento ti ON ti.id = c.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = c.tipo_dinamica_id "
                + "WHERE ti.codigo = 'PU' AND td.codigo = 'ALTERACAO'", Long.class);
    }

    private long procedimentoDaAlteracaoDePu(String codigoTipo) {
        return jdbcTemplate.queryForObject("SELECT p.id FROM instrumento_procedimento_config p "
                + "JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id "
                + "WHERE p.instrumento_dinamica_config_id = ? AND tp.codigo = ?", Long.class, alteracaoDePu(), codigoTipo);
    }

    private long idRede(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM sgifr_rede_defesa WHERE codigo = ?", Long.class, codigo);
    }

    @Test
    void permissaoCcdrNaDecisaoDeInicioDaAlteracaoDePuDeveAparecerNasPermissoesDaSessao() throws Exception {
        long decisao = procedimentoDaAlteracaoDePu("DECISAO_INICIO");

        mockMvc.perform(post(PERMISSOES).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"instrumentoProcedimentoConfigId\":" + decisao + ",\"tipoEntidade\":\"CCDR_DR\",\"ativo\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoProcedimento.codigo").value("DECISAO_INICIO"))
                .andExpect(jsonPath("$.tipoEntidade").value("CCDR_DR"));

        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(daCcdr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItem((int) decisao)));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auditoria WHERE tipo_ocorrencia = 'CONFIGURACAO' "
                + "AND id_ocorrencia LIKE 'permissoes:%' AND acao = 'CRIAR'", Integer.class)).isPositive();
    }

    @Test
    void aMatrizDaAlteracaoDePuVemSoComOsSeusProcedimentosPorOrdem() throws Exception {
        // Os 5 atos pontuais (ordem 101–105) também são Procedimentos da Dinâmica e vêm depois da Alteração.
        List<Long> daDinamica = List.of(procedimentoDaAlteracaoDePu("DECISAO_INICIO"),
                procedimentoDaAlteracaoDePu("ABERTURA_DISCUSSAO_PUBLICA"), procedimentoDaAlteracaoDePu("ALTERACAO"),
                procedimentoDaAlteracaoDePu("ALTERACAO_ATO_ANEXOS"), procedimentoDaAlteracaoDePu("RETIFICACAO_ATO_ANEXOS"),
                procedimentoDaAlteracaoDePu("REVOGACAO_ATO"), procedimentoDaAlteracaoDePu("PRORROGACAO_ATO"),
                procedimentoDaAlteracaoDePu("DECLARACAO_NULIDADE_JUDICIAL"));

        String pagina = mockMvc.perform(get(PERMISSOES).param("instrumentoDinamicaConfigId", String.valueOf(alteracaoDePu()))
                        .param("size", "100").with(comoMaster()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> procedimentos = new ArrayList<>();
        for (JsonNode linha : objectMapper.readTree(pagina).get("content")) {
            procedimentos.add(linha.get("instrumentoProcedimentoConfigId").asLong());
        }
        assertThat(procedimentos).hasSizeGreaterThanOrEqualTo(16).allMatch(daDinamica::contains);
        List<Integer> ordens = procedimentos.stream().map(id -> daDinamica.indexOf(id)).toList();
        assertThat(ordens).isSorted();
        assertThat(procedimentos).containsSubsequence(daDinamica);
    }

    @Test
    void aRedeRvfMudaNomeEIndicadorEORpfgcNaoSeRepete() throws Exception {
        long rvf = idRede("RVF");

        mockMvc.perform(put(REDES + "/" + rvf).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"codigo\":\"RVF\",\"nome\":\"Rede viária florestal (revista)\",\"aplicaPra\":false,"
                                + "\"aplicaPsa\":false,\"publicavel\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaPsa").value(false));
        mockMvc.perform(get(REDES).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("AEMGC"))
                .andExpect(jsonPath("$.content[?(@.codigo=='RVF')].nome").value("Rede viária florestal (revista)"))
                .andExpect(jsonPath("$.content[?(@.codigo=='RVF')].aplicaPsa").value(false));
        assertThat(redeDefesaRepository.findByAplicaPsaTrueOrderByCodigo()).extracting(SgifrRedeDefesa::getCodigo)
                .doesNotContain("RVF");

        mockMvc.perform(post(REDES).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content("{\"codigo\":\"RPFGC\",\"nome\":\"Repetida\",\"aplicaPra\":true,\"aplicaPsa\":false,\"publicavel\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
    }

    @Test
    void osMetodosNovosDosRepositoriosDevolvemOEsperadoSobreOSeed() {
        long decisao = procedimentoDaAlteracaoDePu("DECISAO_INICIO");
        long cmDaDecisao = jdbcTemplate.queryForObject("SELECT id FROM permissao_acao WHERE instrumento_procedimento_config_id = ? "
                + "AND tipo_entidade = 'CM'", Long.class, decisao);
        assertThat(permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidade(decisao, TipoEntidade.CM)).isTrue();
        assertThat(permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidade(decisao, TipoEntidade.IP)).isFalse();
        assertThat(permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidadeAndIdNot(
                decisao, TipoEntidade.CM, cmDaDecisao)).isFalse();
        assertThat(permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidadeAndIdNot(
                decisao, TipoEntidade.CM, -1L)).isTrue();
        List<String> pesquisadas = transacao.execute(estado -> permissaoAcaoRepository.pesquisar(null, decisao,
                TipoEntidade.DGT, true, PageRequest.of(0, 20)).map(p -> p.getTipoEntidade() + ":"
                + p.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo()).getContent());
        assertThat(pesquisadas).containsExactly("DGT:DECISAO_INICIO");
        long totalAtivas = jdbcTemplate.queryForObject("SELECT count(*) FROM permissao_acao WHERE ativo", Long.class);
        assertThat(permissaoAcaoRepository.pesquisar(null, null, null, true, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(totalAtivas);

        long delib = jdbcTemplate.queryForObject("SELECT id FROM tipo_ato WHERE codigo = 'DELIB'", Long.class);
        assertThat(tipoAtoRepository.existsByCodigo("DELIB")).isTrue();
        assertThat(tipoAtoRepository.existsByCodigo("NAOEXISTE")).isFalse();
        assertThat(tipoAtoRepository.existsByCodigoAndIdNot("DELIB", delib)).isFalse();
        assertThat(tipoAtoRepository.existsByCodigoAndIdNot("DELIB", -1L)).isTrue();
        assertThat(tipoAtoRepository.findByAtivo(true, PageRequest.of(0, 100, Sort.by("ordem"))).getTotalElements())
                .isEqualTo(jdbcTemplate.queryForObject("SELECT count(*) FROM tipo_ato WHERE ativo", Long.class));
        assertThat(processoRepository.existsByTipoAto_Id(delib)).isFalse();

        long rpfgc = idRede("RPFGC");
        assertThat(redeDefesaRepository.existsByCodigo("RPFGC")).isTrue();
        assertThat(redeDefesaRepository.existsByCodigo("RXYZ")).isFalse();
        assertThat(redeDefesaRepository.existsByCodigoAndIdNot("RPFGC", rpfgc)).isFalse();
        assertThat(redeDefesaRepository.existsByCodigoAndIdNot("RPFGC", -1L)).isTrue();
        assertThat(redeDefesaRepository.existeEmProcesso(rpfgc)).isFalse();
    }

    @Test
    void osSettersNovosGravamNaBaseOsValoresEnviados() {
        long decisao = procedimentoDaAlteracaoDePu("DECISAO_INICIO");
        long alteracao = procedimentoDaAlteracaoDePu("ALTERACAO");
        Long permissaoId = transacao.execute(estado -> {
            PermissaoAcao permissao = permissaoAcaoRepository.save(new PermissaoAcao(TipoEntidade.SEC_ESTADO,
                    procedimentoConfigRepository.getReferenceById(decisao)));
            permissao.setTipoEntidade(TipoEntidade.APA);
            permissao.setInstrumentoProcedimentoConfig(procedimentoConfigRepository.getReferenceById(alteracao));
            return permissaoAcaoRepository.saveAndFlush(permissao).getId();
        });
        assertThat(jdbcTemplate.queryForMap("SELECT tipo_entidade, instrumento_procedimento_config_id FROM permissao_acao "
                + "WHERE id = ?", permissaoId)).containsEntry("tipo_entidade", "APA")
                .containsEntry("instrumento_procedimento_config_id", alteracao);

        TipoAto tipo = new TipoAto("TMP13", "Temporário", 90);
        tipo.setIncmActTypeIdSerie1(901L);
        long tipoId = tipoAtoRepository.saveAndFlush(tipo).getId();
        TipoAto lido = tipoAtoRepository.findById(tipoId).orElseThrow();
        lido.setCodigo("TMP13B");
        tipoAtoRepository.saveAndFlush(lido);
        assertThat(jdbcTemplate.queryForObject("SELECT codigo FROM tipo_ato WHERE id = ?", String.class, tipoId))
                .isEqualTo("TMP13B");

        long redeId = redeDefesaRepository.saveAndFlush(new SgifrRedeDefesa("RTMP", "Rede", false, false, false)).getId();
        SgifrRedeDefesa rede = redeDefesaRepository.findById(redeId).orElseThrow();
        rede.setCodigo("RTMP2");
        rede.setNome("Rede alterada");
        rede.setAplicaPra(true);
        rede.setAplicaPsa(true);
        rede.setPublicavel(true);
        redeDefesaRepository.saveAndFlush(rede);
        assertThat(jdbcTemplate.queryForObject("SELECT codigo || ':' || nome || ':' || aplica_pra || ':' || aplica_psa "
                + "|| ':' || publicavel FROM sgifr_rede_defesa WHERE id = ?", String.class, redeId))
                .isEqualTo("RTMP2:Rede alterada:true:true:true");
    }
}
