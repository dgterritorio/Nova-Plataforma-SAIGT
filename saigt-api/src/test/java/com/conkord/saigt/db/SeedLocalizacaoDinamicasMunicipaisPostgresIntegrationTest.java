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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A localização das Dinâmicas do município contra o PostgreSQL migrado pelo {@code master.xml}: o que o seed de
 * `pede_localizacao` faz à gravação e à submissão do primeiro Procedimento do PDM, PU e PP.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedLocalizacaoDinamicasMunicipaisPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String FREGUESIA_DE_TESTE = "LOC-TESTE";

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
    private ObjectMapper objectMapper;

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
    void freguesiaDeTeste() {
        jdbcTemplate.update("INSERT INTO lista_valores (tipo, codigo, descricao, ordem) VALUES ('FREGUESIA', ?, "
                + "'Freguesia de teste', 99)", FREGUESIA_DE_TESTE);
    }

    @AfterEach
    void reporListas() {
        jdbcTemplate.update("DELETE FROM lista_valores WHERE tipo = 'FREGUESIA' AND codigo = ?", FREGUESIA_DE_TESTE);
        jdbcTemplate.update("UPDATE lista_valores SET ativo = true WHERE tipo = 'FREGUESIA'");
    }

    @Test
    void aDecisaoDeInicioDaElaboracaoDoPuExigeAFreguesiaOuTodaAAreaQuandoHaFreguesiasAtivas() throws Exception {
        Cenario cenario = cenario("PU", "ELABORACAO", "DECISAO_INICIO");

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("localizacao"));

        gravar(cenario, "\"localizacao\":{\"aplicavelTodaArea\":true}").andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void aDecisaoDeInicioDaElaboracaoDoPuGravaAFreguesiaESubmete() throws Exception {
        Cenario cenario = cenario("PU", "ELABORACAO", "DECISAO_INICIO");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"" + FREGUESIA_DE_TESTE + "\"}").andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(localizacao(cenario).path("freguesia").asText()).isEqualTo(FREGUESIA_DE_TESTE);
    }

    @Test
    void comAListaFreguesiaVaziaADecisaoDeInicioDaElaboracaoDoPuSubmeteSemLocalizacao() throws Exception {
        jdbcTemplate.update("UPDATE lista_valores SET ativo = false WHERE tipo = 'FREGUESIA'");
        Cenario cenario = cenario("PU", "ELABORACAO", "DECISAO_INICIO");

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void aPublicacaoDaSuspensaoDoPdmSubmeteSemFreguesia() throws Exception {
        Cenario cenario = cenario("PDM", "SUSPENSAO", "PUBLICACAO_SUSPENSAO");

        detalhe(cenario)
                .andExpect(jsonPath("$.pedeLocalizacao").value("OPCIONAL"))
                .andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void aPublicacaoDaSuspensaoDoPdmGravaAFreguesiaEOToponimoEmTextoLivre() throws Exception {
        Cenario cenario = cenario("PDM", "SUSPENSAO", "PUBLICACAO_SUSPENSAO");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"" + FREGUESIA_DE_TESTE + "\",\"toponimoTexto\":\"Bairro da Estação\"}")
                .andExpect(status().isOk());

        JsonNode localizacao = localizacao(cenario);
        assertThat(localizacao.path("freguesia").asText()).isEqualTo(FREGUESIA_DE_TESTE);
        assertThat(localizacao.path("toponimoTexto").asText()).isEqualTo("Bairro da Estação");
        submeter(cenario).andExpect(status().isOk());
    }

    @Test
    void aCorrecaoMaterialDoPpGravaAFreguesia() throws Exception {
        Cenario cenario = cenario("PP", "CORRECAO_MATERIAL", "CORRECAO_MATERIAL");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"" + FREGUESIA_DE_TESTE + "\"}").andExpect(status().isOk());

        assertThat(localizacao(cenario).path("freguesia").asText()).isEqualTo(FREGUESIA_DE_TESTE);
    }

    @Test
    void aDecisaoDeInicioDaElaboracaoDoPdmRecusaAFreguesia() throws Exception {
        Cenario cenario = cenario("PDM", "ELABORACAO", "DECISAO_INICIO");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"" + FREGUESIA_DE_TESTE + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("localizacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este procedimento não pede a localização."));
        assertThat(jdbcTemplate.queryForObject("SELECT dados_formulario::text FROM processo WHERE id = ?", String.class,
                cenario.processoId())).isNull();
    }

    @Test
    void oFormularioDaElaboracaoDeUmIgtNovoSabeAntesDeGravarSePedeALocalizacao() throws Exception {
        RequestPostProcessor camara = cenario("PU", "ELABORACAO", "DECISAO_INICIO").autor();

        for (Map.Entry<String, String> esperado : Map.of("PU", "OBRIGATORIA", "PP", "OBRIGATORIA", "PDM", "NAO").entrySet()) {
            long tipoId = jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, esperado.getKey());
            mockMvc.perform(get("/api/v1/tipos-instrumento/" + tipoId + "/elaboracao").with(camara))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.procedimento.tipoProcedimento").value("DECISAO_INICIO"))
                    .andExpect(jsonPath("$.pedeLocalizacao").value(esperado.getValue()));
        }
    }

    private Cenario cenario(String tipoInstrumento, String tipoDinamica, String tipoProcedimento) {
        long procedimentoId = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE ti.codigo = ? AND td.codigo = ? AND tp.codigo = ? AND ipc.ordem = 1", Long.class,
                tipoInstrumento, tipoDinamica, tipoProcedimento);
        long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class,
                CODIGO_CAMARA_DE_SINTRA);
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return transacao.execute(estado -> {
            Utilizador autor = utilizadorRepository.saveAndFlush(
                    new Utilizador("Autora " + sufixo, "autora-" + sufixo + "@teste-loc.pt", "INCM"));
            var procedimento = procedimentoConfigRepository.findById(procedimentoId).orElseThrow();
            var entidade = entidadeRepository.findById(entidadeId).orElseThrow();
            Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(
                    tipoInstrumentoRepository.findAll().stream().filter(t -> tipoInstrumento.equals(t.getCodigo()))
                            .findFirst().orElseThrow(),
                    tipoInstrumento + "-LOC-" + sufixo, "Plano de teste " + sufixo, autor, autor));
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

    private JsonNode localizacao(Cenario cenario) throws Exception {
        String dados = jdbcTemplate.queryForObject("SELECT dados_formulario::text FROM processo WHERE id = ?", String.class,
                cenario.processoId());
        return objectMapper.readTree(dados).path("localizacao");
    }
}
