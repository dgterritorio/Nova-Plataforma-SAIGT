package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um processo em curso por Dinâmica e Procedimento repetível em sequência. Os mocks de beans são os de
 * {@code InstrumentoControllerIntegrationTest} para partilhar o mesmo contexto Spring (e a mesma base H2).
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoEmCursoPorDinamicaIntegrationTest {

    private static final String MENSAGEM_DINAMICA = "Já existe um processo em curso nesta dinâmica.";
    private static final AtomicLong PROXIMO_TIPO_ATO_INCM = new AtomicLong(9_300_000L);

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

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

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Cenario(Entidade entidade, Utilizador autor, Instrumento instrumento, Dinamica dinamica,
                           InstrumentoProcedimentoConfig discussao, InstrumentoProcedimentoConfig publicacao,
                           InstrumentoProcedimentoConfig alternativo, InstrumentoProcedimentoConfig pontual,
                           InstrumentoProcedimentoConfig semPermissao, TipoAto tipoAto) {
    }

    @Test
    void deveRecusarOProcedimentoSeguinteComADiscussaoPublicaEnviadaADgt() throws Exception {
        Cenario cenario = criarCenario("PEC1", "501900001", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ENVIADO_DGT, 1);

        criar(cenario, cenario.publicacao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"))
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_DINAMICA));

        assertThat(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
                cenario.dinamica(), 1, List.of(cenario.publicacao()))).isEmpty();
    }

    @Test
    void deveRecusarOutroProcedimentoComADiscussaoPublicaRejeitadaPelaDgt() throws Exception {
        Cenario cenario = criarCenario("PEC2", "501900002", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.REJEITADO_DGT, 1);

        criar(cenario, cenario.publicacao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    @Test
    void deveRecusarOutroProcedimentoComADiscussaoPublicaAnuladaPelaIncm() throws Exception {
        Cenario cenario = criarCenario("PEC3", "501900003", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ANULADO_INCM, 1);

        criar(cenario, cenario.publicacao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    @Test
    void deveCriarNovaOcorrenciaDaDiscussaoPublicaDepoisDeAnulada() throws Exception {
        Cenario cenario = criarCenario("PEC4", "501900004", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ANULADO, 1);

        criar(cenario, cenario.discussao())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrencia").value(2));
    }

    @Test
    void deveOferecerADiscussaoPublicaRepetivelPublicadaECriarAOcorrenciaSeguinte() throws Exception {
        Cenario cenario = criarCenario("PEC5", "501900005", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.PUBLICADO, 1);

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.instrumento().getId() + "/disponivel").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].instrumentoProcedimentoConfigId",
                        hasItems(cenario.discussao().getId().intValue(), cenario.publicacao().getId().intValue())));

        criar(cenario, cenario.discussao())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrencia").value(2));
    }

    @Test
    void deveRecusarANovaOcorrenciaDeUmProcedimentoNaoRepetivelPublicado() throws Exception {
        Cenario cenario = criarCenario("PEC6", "501900006", false);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.PUBLICADO, 1);

        criar(cenario, cenario.discussao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    @Test
    void deveTrazerNoDisponivelSoOProcedimentoEmSubmissaoEOsAtosPontuais() throws Exception {
        Cenario cenario = criarCenario("PEC7", "501900007", true);
        decisaoPublicada(cenario);
        Processo rascunho = criarProcesso(cenario, cenario.discussao(), EstadoProcesso.EM_SUBMISSAO, 1);

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.instrumento().getId() + "/disponivel").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(2)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].instrumentoProcedimentoConfigId",
                        containsInAnyOrder(cenario.discussao().getId().intValue(), cenario.pontual().getId().intValue())))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[?(@.atoPontual == false)].tentativaEmCurso.processoId",
                        containsInAnyOrder(rascunho.getId().toString())));
    }

    @Test
    void deveTrazerNoDisponivelSoOsAtosPontuaisComUmProcessoEnviadoADgt() throws Exception {
        Cenario cenario = criarCenario("PEC8", "501900008", true);
        decisaoPublicada(cenario);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ENVIADO_DGT, 1);

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.instrumento().getId() + "/disponivel").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].instrumentoProcedimentoConfigId",
                        containsInAnyOrder(cenario.pontual().getId().intValue())));
    }

    @Test
    void naoDeveRecusarUmAtoPontualPorHaverOutroProcessoEmCurso() throws Exception {
        Cenario cenario = criarCenario("PEC9", "501900009", true);
        Processo decisao = decisaoPublicada(cenario);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ENVIADO_DGT, 1);

        criar(cenario, cenario.pontual(), decisao.getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.processoReferidoId").value(decisao.getId().toString()));
    }

    @Test
    void naoDeveRecusarOProcedimentoSeguinteComSoUmAtoPontualEmCurso() throws Exception {
        Cenario cenario = criarCenario("PEC10", "501900010", true);
        criarProcesso(cenario, cenario.pontual(), EstadoProcesso.ENVIADO_DGT, 1);

        criar(cenario, cenario.discussao()).andExpect(status().isCreated());
    }

    @Test
    void deveRecusarNovaOcorrenciaDoMesmoProcedimentoEmValidacaoDgt() throws Exception {
        Cenario cenario = criarCenario("PEC11", "501900011", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.EM_VALIDACAO_DGT, 1);

        criar(cenario, cenario.discussao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    @Test
    void deveDevolver403SemPermissaoAntesDaRegraDoProcessoEmCurso() throws Exception {
        Cenario cenario = criarCenario("PEC12", "501900012", true);
        criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ENVIADO_DGT, 1);

        criar(cenario, cenario.semPermissao()).andExpect(status().isForbidden());
    }

    @Test
    void deveDevolver401SemToken() throws Exception {
        Cenario cenario = criarCenario("PEC13", "501900013", true);

        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarProcessoRequest(cenario.discussao().getId(), cenario.tipoAto().getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveDeixarPassarSoUmDosDoisPedidosSimultaneosDeProcedimentosDiferentes() throws Exception {
        Cenario cenario = criarCenario("PEC14", "501900014", true);
        CountDownLatch arranque = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> pedidos = new ArrayList<>();
            for (InstrumentoProcedimentoConfig config : List.of(cenario.discussao(), cenario.alternativo())) {
                Callable<MvcResult> pedido = () -> {
                    arranque.await();
                    return criar(cenario, config).andReturn();
                };
                pedidos.add(pool.submit(pedido));
            }
            arranque.countDown();

            List<MvcResult> resultados = new ArrayList<>();
            for (Future<MvcResult> pedido : pedidos) {
                resultados.add(pedido.get(30, TimeUnit.SECONDS));
            }

            assertThat(resultados).extracting(resultado -> resultado.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            MvcResult recusado = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
            JsonNode corpo = objectMapper.readTree(recusado.getResponse().getContentAsString());
            assertThat(corpo.get("codigo").asText()).isEqualTo("PROCESSO_EM_CURSO");
            assertThat(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
                    cenario.dinamica(), 1, List.of(cenario.discussao(), cenario.alternativo()))).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deveSeguirOSequenciamentoDesdeOInicioNaSegundaTentativaSemContarOsProcessosDaPrimeira() throws Exception {
        Cenario cenario = criarCenario("PEC21", "501900021", false);
        Processo discussaoDaPrimeira = criarProcesso(cenario, cenario.discussao(), EstadoProcesso.PUBLICADO, 1);
        Processo publicacaoDaPrimeira = criarProcesso(cenario, cenario.publicacao(), EstadoProcesso.ENVIADO_DGT, 1);
        jdbcTemplate.update("UPDATE dinamica SET tentativa = 2 WHERE id = ?", cenario.dinamica().getId());

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.instrumento().getId() + "/disponivel").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].instrumentoProcedimentoConfigId",
                        hasItems(cenario.discussao().getId().intValue())))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].instrumentoProcedimentoConfigId",
                        not(hasItems(cenario.publicacao().getId().intValue()))))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[?(@.atoPontual == false)].tentativaEmCurso",
                        everyItem(nullValue())));

        criar(cenario, cenario.publicacao())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEQUENCIAMENTO_NAO_CUMPRIDO"));
        String corpo = criar(cenario, cenario.discussao())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrencia").value(1))
                .andReturn().getResponse().getContentAsString();

        Processo daSegunda = processoRepository.findById(UUID.fromString(objectMapper.readTree(corpo).get("id").asText()))
                .orElseThrow();
        assertThat(daSegunda.getTentativa()).isEqualTo(2);
        assertThat(daSegunda.getAproveitaAtosAnteriores()).isNull();
        assertThat(processoRepository.findById(discussaoDaPrimeira.getId()).orElseThrow())
                .extracting(Processo::getTentativa, Processo::getOcorrencia, Processo::getEstado)
                .containsExactly(1, 1, EstadoProcesso.PUBLICADO);
        assertThat(processoRepository.findById(publicacaoDaPrimeira.getId()).orElseThrow())
                .extracting(Processo::getTentativa, Processo::getEstado)
                .containsExactly(1, EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveTrazerNoDetalheOAproveitamentoDosAtosAnterioresSoQuandoFoiIndicado() throws Exception {
        Cenario cenario = criarCenario("PEC22", "501900022", false);
        Processo daPrimeira = criarProcesso(cenario, cenario.discussao(), EstadoProcesso.ANULADO, 1);
        jdbcTemplate.update("UPDATE dinamica SET tentativa = 2 WHERE id = ?", cenario.dinamica().getId());
        String corpo = criar(cenario, cenario.discussao()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID daSegunda = UUID.fromString(objectMapper.readTree(corpo).get("id").asText());

        mockMvc.perform(get("/api/v1/processos/" + daSegunda).with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aproveitaAtosAnteriores").value(nullValue()));

        jdbcTemplate.update("UPDATE processo SET aproveita_atos_anteriores = true WHERE id = ?", daSegunda);
        mockMvc.perform(get("/api/v1/processos/" + daSegunda).with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aproveitaAtosAnteriores").value(true));
        mockMvc.perform(get("/api/v1/processos/" + daPrimeira.getId()).with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aproveitaAtosAnteriores").value(nullValue()));
    }

    private ResultActions criar(Cenario cenario, InstrumentoProcedimentoConfig config) throws Exception {
        return criar(cenario, config, null);
    }

    private ResultActions criar(Cenario cenario, InstrumentoProcedimentoConfig config, UUID processoReferidoId) throws Exception {
        return mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos").with(jwtDe(cenario))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                        new CriarProcessoRequest(config.getId(), cenario.tipoAto().getId(), processoReferidoId))));
    }

    /**
     * Uma Decisão de início `PUBLICADO` na Dinâmica, o ato que os atos pontuais visam; opcional e sem `permissao_acao`,
     * para não mudar o sequenciamento nem o que o `/disponivel` oferece.
     */
    private Processo decisaoPublicada(Cenario cenario) {
        TipoProcedimento tipo = tipoProcedimentoRepository.findAll().stream()
                .filter(candidato -> "DECISAO_INICIO".equals(candidato.getCodigo())).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início")));
        InstrumentoProcedimentoConfig decisao = new InstrumentoProcedimentoConfig(
                cenario.dinamica().getInstrumentoDinamicaConfig(), tipo, 1, "SEGUNDA_H");
        decisao.setObrigatorio(false);
        decisao = procedimentoConfigRepository.saveAndFlush(decisao);
        return criarProcesso(cenario, decisao, EstadoProcesso.PUBLICADO, 1);
    }

    /** Um por pedido: os pedidos simultâneos não partilham o post-processor. */
    private RequestPostProcessor jwtDe(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", "Entidade Teste", "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private Processo criarProcesso(Cenario cenario, InstrumentoProcedimentoConfig config, EstadoProcesso estado, int ocorrencia) {
        Processo processo = new Processo(cenario.dinamica(), config, cenario.autor(), cenario.entidade());
        processo.setEstado(estado);
        processo.setOcorrencia(ocorrencia);
        return processoRepository.saveAndFlush(processo);
    }

    /**
     * Dinâmica com cinco Procedimentos: a Discussão Pública (ordem 1) e um alternativo (ordem 1, opcional) que
     * se criam sem sequenciamento, a Publicação (ordem 2, depende da Discussão), um ato pontual (ordem 1,
     * opcional) e um sem `permissao_acao`. Os códigos e o NIF têm de ser únicos por teste: a base H2 é partilhada.
     */
    private Cenario criarCenario(String codigo, String nif, boolean discussaoRepetivel) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));

        InstrumentoProcedimentoConfig discussao = procedimento(dinamicaConfig, "DISC-" + codigo, 1, true, false, true);
        discussao.setRepetivel(discussaoRepetivel);
        discussao = procedimentoConfigRepository.saveAndFlush(discussao);
        InstrumentoProcedimentoConfig publicacao = procedimento(dinamicaConfig, "PUB-" + codigo, 2, true, false, true);
        InstrumentoProcedimentoConfig alternativo = procedimento(dinamicaConfig, "ALTV-" + codigo, 1, false, false, true);
        InstrumentoProcedimentoConfig pontual = procedimento(dinamicaConfig, "PONT-" + codigo, 1, false, true, true);
        InstrumentoProcedimentoConfig semPermissao = procedimento(dinamicaConfig, "SEMP-" + codigo, 1, false, false, false);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor-" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        TipoAto tipoAto = new TipoAto("ATO-" + codigo, "Ato " + codigo, 1);
        tipoAto.setIncmActTypeIdSerie2(PROXIMO_TIPO_ATO_INCM.incrementAndGet());
        tipoAto = tipoAtoRepository.saveAndFlush(tipoAto);

        return new Cenario(entidade, autor, instrumento, dinamica, discussao, publicacao, alternativo, pontual, semPermissao, tipoAto);
    }

    private InstrumentoProcedimentoConfig procedimento(InstrumentoDinamicaConfig dinamicaConfig, String codigo, int ordem,
                                                       boolean obrigatorio, boolean atoPontual, boolean comPermissao) {
        TipoProcedimento tipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig, tipo, ordem, "SEGUNDA_H");
        config.setObrigatorio(obrigatorio);
        config.setAtoPontual(atoPontual);
        config = procedimentoConfigRepository.saveAndFlush(config);
        if (comPermissao) {
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config));
        }
        return config;
    }
}
