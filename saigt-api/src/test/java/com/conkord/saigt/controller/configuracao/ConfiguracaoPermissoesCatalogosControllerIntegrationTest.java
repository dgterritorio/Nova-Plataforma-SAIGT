package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.LinksPublicosIncm;
import com.conkord.saigt.service.incm.MontadorPedidoAtoIncm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base H2 partilhada com outras classes: cada teste cria os seus registos com códigos únicos (nunca os códigos do
 * seed, como RVF ou RPFGC, que outros testes inserem).
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracaoPermissoesCatalogosControllerIntegrationTest {

    private static final String PERMISSOES = "/api/v1/configuracao/permissoes";
    private static final String TIPOS_ATO = "/api/v1/configuracao/tipos-ato";
    private static final String REDES = "/api/v1/configuracao/redes-defesa";

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
    private TipoAtoRepository tipoAtoRepository;
    @Autowired
    private SgifrRedeDefesaRepository redeDefesaRepository;
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
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private MontadorPedidoAtoIncm montadorPedidoAtoIncm;

    private static String codigoCurto() {
        return "Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static RequestPostProcessor comoMaster() {
        return comoPerfil("MASTER");
    }

    private static RequestPostProcessor comoPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor daEntidade(String tipoEntidade, Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", tipoEntidade, "id", entidade.getId(),
                        "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private ResultActions postar(String url, Object corpo) throws Exception {
        return mockMvc.perform(post(url).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)));
    }

    private ResultActions atualizar(String url, long id, Object corpo) throws Exception {
        return mockMvc.perform(put(url + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)));
    }

    private long idDe(ResultActions resposta) throws Exception {
        return objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> corpoPermissao(Object procedimentoId, Object tipoEntidade, Object ativo) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", procedimentoId);
        corpo.put("tipoEntidade", tipoEntidade);
        corpo.put("ativo", ativo);
        return corpo;
    }

    private static Map<String, Object> corpoTipoAto(String codigo, Long serie1, Long serie2) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("codigo", codigo);
        corpo.put("nome", "Tipo de ato " + codigo);
        corpo.put("incmActTypeIdSerie1", serie1);
        corpo.put("incmActTypeIdSerie2", serie2);
        corpo.put("ordem", 50);
        corpo.put("ativo", true);
        return corpo;
    }

    private static Map<String, Object> corpoRede(String codigo, String nome, boolean aplicaPra, boolean aplicaPsa) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("codigo", codigo);
        corpo.put("nome", nome);
        corpo.put("aplicaPra", aplicaPra);
        corpo.put("aplicaPsa", aplicaPsa);
        corpo.put("publicavel", true);
        return corpo;
    }

    private InstrumentoDinamicaConfig novaConfigDinamica() {
        return dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoCurto(), "Plano Urbano", "PLANO")),
                tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoCurto(), "Alteração"))));
    }

    private InstrumentoProcedimentoConfig novoProcedimento(InstrumentoDinamicaConfig config, int ordem) {
        return procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(config,
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigoCurto(), "Procedimento " + ordem)),
                ordem, "SEGUNDA_H"));
    }

    private record Autor(Entidade entidade, Utilizador utilizador) {
    }

    private Autor novoAutor() {
        String nipc = String.valueOf(500_000_000L + (long) (Math.random() * 99_999_999L));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nipc, "Câmara de Teste"));
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora", "autora" + codigoCurto().toLowerCase() + "@cm-teste.pt", "INCM"));
        return new Autor(entidade, utilizador);
    }

    private Dinamica novaDinamica(InstrumentoDinamicaConfig config, Utilizador autor) {
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(config.getTipoInstrumento(), "1", "2026/CFG/" + codigoCurto(), autor, autor));
        return dinamicaRepository.saveAndFlush(new Dinamica(instrumento, config, "Alteração", autor));
    }

    private TipoAto novoTipoAto(long serie2) {
        TipoAto tipo = new TipoAto(codigoCurto(), "Deliberação", 60);
        tipo.setIncmActTypeIdSerie2(serie2);
        return tipoAtoRepository.saveAndFlush(tipo);
    }

    private Processo novoProcesso(InstrumentoProcedimentoConfig procedimento, TipoAto tipoAto) {
        Autor autor = novoAutor();
        Dinamica dinamica = novaDinamica(procedimento.getInstrumentoDinamicaConfig(), autor.utilizador());
        Processo processo = new Processo(dinamica, procedimento, autor.utilizador(), autor.entidade());
        processo.setTipoAto(tipoAto);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        return processoRepository.saveAndFlush(processo);
    }

    @Test
    void masterDeveDarPermissaoACcdrQuePassaAVerOProcedimentoNasPermissoesDaSessao() throws Exception {
        InstrumentoProcedimentoConfig decisao = novoProcedimento(novaConfigDinamica(), 1);
        Autor ccdr = novoAutor();

        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(daEntidade("CCDR_DR", ccdr.entidade(), ccdr.utilizador().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procedimentosPermitidos", not(hasItem(decisao.getId().intValue()))));

        postar(PERMISSOES, corpoPermissao(decisao.getId(), "CCDR_DR", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.instrumentoProcedimentoConfigId").value(decisao.getId()))
                .andExpect(jsonPath("$.tipoProcedimento.codigo").value(decisao.getTipoProcedimento().getCodigo()))
                .andExpect(jsonPath("$.tipoEntidade").value("CCDR_DR"))
                .andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(daEntidade("CCDR_DR", ccdr.entidade(), ccdr.utilizador().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEntidade").value("CCDR_DR"))
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItem(decisao.getId().intValue())));
    }

    @Test
    void desativarALinhaCmDeveRecusarACriacaoSeguinteDoProcessoSemReiniciar() throws Exception {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        InstrumentoProcedimentoConfig decisao = novoProcedimento(config, 1);
        PermissaoAcao cm = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, decisao));
        Autor autor = novoAutor();
        Dinamica dinamica = novaDinamica(config, autor.utilizador());
        TipoAto tipoAto = novoTipoAto(70_001L);
        RequestPostProcessor sessaoCm = daEntidade("CM", autor.entidade(), autor.utilizador().getId());

        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(sessaoCm))
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItem(decisao.getId().intValue())));

        atualizar(PERMISSOES, cm.getId(), corpoPermissao(decisao.getId(), "CM", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(sessaoCm)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CriarProcessoRequest(decisao.getId(), tipoAto.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(sessaoCm))
                .andExpect(jsonPath("$.procedimentosPermitidos", not(hasItem(decisao.getId().intValue()))));
        assertThat(processoRepository.configsComProcessos(List.of(decisao.getId()))).isEmpty();
    }

    @Test
    void parRepetidoDeveDar400NoTipoDeEntidadeTambemNoPut() throws Exception {
        InstrumentoProcedimentoConfig decisao = novoProcedimento(novaConfigDinamica(), 1);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, decisao));
        PermissaoAcao dgt = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, decisao));

        postar(PERMISSOES, corpoPermissao(decisao.getId(), "CM", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoEntidade"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este tipo de entidade já tem permissão neste Procedimento."));
        atualizar(PERMISSOES, dgt.getId(), corpoPermissao(decisao.getId(), "CM", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoEntidade"));
        assertThat(permissaoAcaoRepository.findById(dgt.getId()).orElseThrow().getTipoEntidade()).isEqualTo(TipoEntidade.DGT);
    }

    @Test
    void tipoDeEntidadeForaDaListaOuProcedimentoInexistenteDevemDar400() throws Exception {
        InstrumentoProcedimentoConfig decisao = novoProcedimento(novaConfigDinamica(), 1);

        postar(PERMISSOES, corpoPermissao(decisao.getId(), "ENTIDADE", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        postar(PERMISSOES, corpoPermissao(999_999_999L, "CM", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoProcedimentoConfigId"));
        postar(PERMISSOES, corpoPermissao(decisao.getId(), "CM", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("ativo"));
        mockMvc.perform(get(PERMISSOES).param("tipoEntidade", "ENTIDADE").with(comoMaster()))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM permissao_acao WHERE instrumento_procedimento_config_id = ?",
                Integer.class, decisao.getId())).isZero();
    }

    @Test
    void listagemPorDinamicaDeveTrazerSoOsSeusProcedimentosPorOrdemEPorTipoDeEntidade() throws Exception {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        InstrumentoProcedimentoConfig terceiro = novoProcedimento(config, 3);
        InstrumentoProcedimentoConfig primeiro = novoProcedimento(config, 1);
        InstrumentoProcedimentoConfig segundo = novoProcedimento(config, 2);
        for (InstrumentoProcedimentoConfig procedimento : List.of(terceiro, primeiro, segundo)) {
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, procedimento));
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        }
        InstrumentoProcedimentoConfig outro = novoProcedimento(novaConfigDinamica(), 1);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, outro));

        String pagina = mockMvc.perform(get(PERMISSOES).param("instrumentoDinamicaConfigId", String.valueOf(config.getId()))
                        .with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andReturn().getResponse().getContentAsString();
        List<String> linhas = new ArrayList<>();
        for (JsonNode linha : objectMapper.readTree(pagina).get("content")) {
            linhas.add(linha.get("instrumentoProcedimentoConfigId").asLong() + ":" + linha.get("tipoEntidade").asText());
        }
        assertThat(linhas).containsExactly(primeiro.getId() + ":CM", primeiro.getId() + ":DGT",
                segundo.getId() + ":CM", segundo.getId() + ":DGT", terceiro.getId() + ":CM", terceiro.getId() + ":DGT");

        mockMvc.perform(get(PERMISSOES).param("instrumentoProcedimentoConfigId", String.valueOf(segundo.getId()))
                        .param("tipoEntidade", "DGT").param("ativo", "true").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipoEntidade").value("DGT"))
                .andExpect(jsonPath("$.content[0].tipoProcedimento.nome").value("Procedimento 2"));
    }

    @Test
    void tipoDeAtoSemSeriesDeveDar400EComASegundaSerieDeveDar201() throws Exception {
        String codigo = codigoCurto();

        postar(TIPOS_ATO, corpoTipoAto(codigo, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Indique pelo menos uma série."))
                .andExpect(jsonPath("$.campos[0].path").value("incmActTypeIdSerie1"))
                .andExpect(jsonPath("$.campos[1].path").value("incmActTypeIdSerie2"));
        assertThat(tipoAtoRepository.existsByCodigo(codigo)).isFalse();

        long id = idDe(postar(TIPOS_ATO, corpoTipoAto(codigo, null, 45L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.incmActTypeIdSerie1").doesNotExist())
                .andExpect(jsonPath("$.incmActTypeIdSerie2").value(45))
                .andExpect(jsonPath("$.ordem").value(50))
                .andExpect(jsonPath("$.ativo").value(true)));
        Map<String, Object> linha = jdbcTemplate.queryForMap(
                "SELECT incm_act_type_id_serie_1, incm_act_type_id_serie_2 FROM tipo_ato WHERE id = ?", id);
        assertThat(linha.get("incm_act_type_id_serie_1")).isNull();
        assertThat(((Number) linha.get("incm_act_type_id_serie_2")).longValue()).isEqualTo(45L);

        postar(TIPOS_ATO, corpoTipoAto(codigo, 1L, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        postar(TIPOS_ATO, corpoTipoAto("minusc", 1L, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        postar(TIPOS_ATO, corpoTipoAto(codigoCurto(), 0L, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("incmActTypeIdSerie1"));
    }

    @Test
    void tipoDeAtoDeUmProcessoNaoMudaDeCodigoMasOActTypeIdNovoSegueParaAIncm() throws Exception {
        TipoAto tipoAto = novoTipoAto(43L);
        InstrumentoProcedimentoConfig procedimento = novoProcedimento(novaConfigDinamica(), 1);
        Processo processo = novoProcesso(procedimento, tipoAto);

        atualizar(TIPOS_ATO, tipoAto.getId(), corpoTipoAto(codigoCurto(), null, 43L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));
        assertThat(tipoAtoRepository.findById(tipoAto.getId()).orElseThrow().getCodigo()).isEqualTo(tipoAto.getCodigo());
        assertThat(processoRepository.existsByTipoAto_Id(tipoAto.getId())).isTrue();
        assertThat(processoRepository.existsByTipoAto_Id(novoTipoAto(44L).getId())).isFalse();

        atualizar(TIPOS_ATO, tipoAto.getId(), corpoTipoAto(tipoAto.getCodigo(), null, 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incmActTypeIdSerie2").value(99));

        Long actTypeIdEnviado = transacao.execute(estado -> montadorPedidoAtoIncm.montar(
                        processoRepository.findById(processo.getId()).orElseThrow(),
                        new LinksPublicosIncm.LinksIncm(List.of(), List.of()))
                .dgtAct().diarioRepublicaSeries2Act().actTypeId());
        assertThat(actTypeIdEnviado).isEqualTo(99L);
    }

    @Test
    void redeDeveMudarNomeEIndicadoresECodigoRepetidoDeveDar400() throws Exception {
        String codigo = codigoCurto();
        long id = idDe(postar(REDES, corpoRede(codigo, "Rede viária", false, true)).andExpect(status().isCreated()));

        atualizar(REDES, id, corpoRede(codigo, "Rede viária florestal (revista)", false, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Rede viária florestal (revista)"))
                .andExpect(jsonPath("$.aplicaPsa").value(false));
        mockMvc.perform(get(REDES).param("size", "100").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].nome").value("Rede viária florestal (revista)"))
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].aplicaPsa").value(false));

        postar(REDES, corpoRede(codigo, "Outra", true, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        postar(REDES, corpoRede("CODIGO_LONGO", "Outra", true, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
    }

    @Test
    void redeEscolhidaNumProcessoNaoMudaDeCodigo() throws Exception {
        SgifrRedeDefesa rede = redeDefesaRepository.saveAndFlush(
                new SgifrRedeDefesa(codigoCurto(), "Rede de pontos de água", false, true, true));
        Processo processo = novoProcesso(novoProcedimento(novaConfigDinamica(), 1), null);
        jdbcTemplate.update("INSERT INTO processo_rede_defesa (processo_id, rede_defesa_id) VALUES (?, ?)",
                processo.getId(), rede.getId());

        atualizar(REDES, rede.getId(), corpoRede(codigoCurto(), rede.getNome(), false, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));
        assertThat(redeDefesaRepository.findById(rede.getId()).orElseThrow().getCodigo()).isEqualTo(rede.getCodigo());
        assertThat(redeDefesaRepository.existeEmProcesso(rede.getId())).isTrue();
        atualizar(REDES, rede.getId(), corpoRede(rede.getCodigo(), rede.getNome(), true, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaPra").value(true));
    }

    @Test
    void tecnicoDeveDar403EmTodosOsEndpointsESemSessao401() throws Exception {
        InstrumentoProcedimentoConfig decisao = novoProcedimento(novaConfigDinamica(), 1);
        PermissaoAcao permissao = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, decisao));
        TipoAto tipoAto = novoTipoAto(71_001L);
        SgifrRedeDefesa rede = redeDefesaRepository.saveAndFlush(new SgifrRedeDefesa(codigoCurto(), "Rede", false, true, true));
        Map<String, String> corpos = Map.of(
                PERMISSOES, json(corpoPermissao(decisao.getId(), "DGT", true)),
                TIPOS_ATO, json(corpoTipoAto(codigoCurto(), null, 1L)),
                REDES, json(corpoRede(codigoCurto(), "Rede", false, true)));
        Map<String, Long> ids = Map.of(PERMISSOES, permissao.getId(), TIPOS_ATO, tipoAto.getId(), REDES, rede.getId());

        for (RequestPostProcessor ator : List.of(comoPerfil("TECNICO"), comoPerfil("VISITANTE"))) {
            for (String url : List.of(PERMISSOES, TIPOS_ATO, REDES)) {
                for (ResultActions resposta : List.of(
                        mockMvc.perform(get(url).with(ator)),
                        mockMvc.perform(post(url).with(ator).contentType(APPLICATION_JSON).content(corpos.get(url))),
                        mockMvc.perform(put(url + "/" + ids.get(url)).with(ator).contentType(APPLICATION_JSON)
                                .content(corpos.get(url))))) {
                    resposta.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
                }
            }
        }
        for (String url : List.of(PERMISSOES, TIPOS_ATO, REDES)) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        assertThat(permissaoAcaoRepository.findById(permissao.getId()).orElseThrow().getTipoEntidade()).isEqualTo(TipoEntidade.CM);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", "permissoes:" + permissao.getId())).isEmpty();
    }

    @Test
    void cadaEscritaDeveFicarAuditadaComORecursoOIdEOsCamposAlterados() throws Exception {
        InstrumentoProcedimentoConfig decisao = novoProcedimento(novaConfigDinamica(), 1);
        long permissaoId = idDe(postar(PERMISSOES, corpoPermissao(decisao.getId(), "AM", true)).andExpect(status().isCreated()));
        atualizar(PERMISSOES, permissaoId, corpoPermissao(decisao.getId(), "AM", false)).andExpect(status().isOk());

        String codigo = codigoCurto();
        long tipoAtoId = idDe(postar(TIPOS_ATO, corpoTipoAto(codigo, 7L, null)).andExpect(status().isCreated()));
        atualizar(TIPOS_ATO, tipoAtoId, corpoTipoAto(codigo, 7L, 8L)).andExpect(status().isOk());

        String codigoRede = codigoCurto();
        long redeId = idDe(postar(REDES, corpoRede(codigoRede, "Rede", false, true)).andExpect(status().isCreated()));
        atualizar(REDES, redeId, corpoRede(codigoRede, "Rede alterada", false, true)).andExpect(status().isOk());
        atualizar(REDES, redeId, corpoRede(codigoRede, "Rede alterada", false, true)).andExpect(status().isOk());

        assertThat(auditada("permissoes:" + permissaoId, "ATUALIZAR")).contains("\"ativo\"").contains("false")
                .doesNotContain("tipoEntidade");
        assertThat(auditada("tipos-ato:" + tipoAtoId, "ATUALIZAR")).contains("incmActTypeIdSerie2").doesNotContain("codigo");
        assertThat(auditada("redes-defesa:" + redeId, "ATUALIZAR")).contains("Rede alterada").doesNotContain("aplicaPsa");
        assertThat(auditada("permissoes:" + permissaoId, "CRIAR")).contains("AM");
        assertThat(auditada("tipos-ato:" + tipoAtoId, "CRIAR")).contains(codigo);
        assertThat(auditada("redes-defesa:" + redeId, "CRIAR")).contains(codigoRede);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", "redes-defesa:" + redeId))
                .hasSize(2).allSatisfy(linha -> assertThat(linha.getUtilizadorId()).isNotNull());
    }

    private String auditada(String idOcorrencia, String acao) {
        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", idOcorrencia)
                .stream().filter(linha -> linha.getAcao().equals(acao)).toList();
        assertThat(linhas).hasSize(1);
        return linhas.get(0).getDadosAlteracao();
    }
}
