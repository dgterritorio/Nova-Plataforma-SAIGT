package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O tipo de ato escolhe-se no cabeçalho: o processo nasce sem ele, grava-se no rascunho, é obrigatório para submeter e
 * fica bloqueado depois da primeira submissão. Os mocks de beans são os de {@code ProcessoEmCursoPorDinamicaIntegrationTest},
 * para partilhar o contexto Spring (e a base H2); os códigos e os NIF são únicos por teste.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TipoAtoNoCabecalhoIntegrationTest {

    private static final AtomicLong PROXIMO_ACT_TYPE = new AtomicLong(9_368_000L);

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

    /** `autorizado` está no claim `atos_autorizados` da entidade; `naoAutorizado` é da mesma série e não está. */
    private record Cenario(Entidade entidade, Utilizador autor, Instrumento instrumento, Dinamica dinamica,
                           InstrumentoProcedimentoConfig procedimento, TipoAto autorizado, TipoAto naoAutorizado) {
    }

    @Test
    void criaSoComOProcedimentoETipoAtoNulo() throws Exception {
        Cenario cenario = criarCenario("TAC1", "501368001");

        String corpo = criar(cenario, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAtoId").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertThat(processoRepository.findById(idDe(corpo)).orElseThrow().getTipoAto()).isNull();
    }

    @Test
    void semTipoAtoRecusaNaCriacaoAEntidadeDeOutraParteDoDr() throws Exception {
        Cenario cenario = criarCenario("TAC7", "501368007");
        Map<String, Object> corpo = Map.of("instrumentoProcedimentoConfigId", cenario.procedimento().getId());

        mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos").with(jwtDe(cenario, atosDe(cenario), "C"))
                        .contentType("application/json").content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PARTE_DR_NAO_AUTORIZADA"));
        assertThat(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
                cenario.dinamica(), 1, List.of(cenario.procedimento()))).isEmpty();
    }

    @Test
    void criaComUmTipoAutorizadoERecusaOInexistenteEONaoAutorizado() throws Exception {
        Cenario cenario = criarCenario("TAC2", "501368002");

        criar(cenario, -1L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Tipo de Ato não encontrado."));
        criar(cenario, cenario.naoAutorizado().getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TIPO_ATO_NAO_AUTORIZADO"));
        criar(cenario, cenario.autorizado().getId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAtoId").value(cenario.autorizado().getId()));
    }

    @Test
    void oDetalheTrazOsTiposDoDisponivelESemTipoAtoBloqueadoAntesDaSubmissao() throws Exception {
        Cenario cenario = criarCenario("TAC3", "501368003");
        UUID processo = idDe(criar(cenario, null).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtComAtos(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAtoNome").value(nullValue()))
                .andExpect(jsonPath("$.camposBloqueados", not(hasItem("tipoAtoId"))))
                .andExpect(jsonPath("$.tiposAtoDisponiveis[*].id", contains(cenario.autorizado().getId().intValue())))
                .andExpect(jsonPath("$.tiposAtoDisponiveis[0].nome").value(cenario.autorizado().getNome()));
        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.instrumento().getId() + "/disponivel").with(jwtComAtos(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tiposAtoDisponiveis[*].id",
                        contains(cenario.autorizado().getId().intValue())));
    }

    @Test
    void gravaNoRascunhoUmTipoDosDisponiveisERecusaOsOutrosSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("TAC4", "501368004");
        UUID processo = idDe(criar(cenario, null).andReturn().getResponse().getContentAsString());

        gravar(processo, jwtComAtos(cenario), cenario.naoAutorizado().getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TIPO_ATO_NAO_AUTORIZADO"));
        gravar(processo, jwtComAtos(cenario), -1L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Tipo de Ato não encontrado."));
        assertThat(processoRepository.findById(processo).orElseThrow())
                .extracting(Processo::getTipoAto, Processo::getSumario).containsExactly(null, null);

        gravar(processo, jwtComAtos(cenario), cenario.autorizado().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAtoId").value(cenario.autorizado().getId()));
        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtComAtos(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAtoId").value(cenario.autorizado().getId()))
                .andExpect(jsonPath("$.tipoAtoNome").value(cenario.autorizado().getNome()));
    }

    @Test
    void umUtilizadorSemClaimDeAtosGravaQualquerTipoDaSerie() throws Exception {
        Cenario cenario = criarCenario("TAC5", "501368005");
        UUID processo = idDe(criar(cenario, null).andReturn().getResponse().getContentAsString());

        gravar(processo, jwtSemAtos(cenario), cenario.naoAutorizado().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAtoId").value(cenario.naoAutorizado().getId()));
    }

    @Test
    void naoSubmeteSemTipoAtoESubmeteDepoisDeOEscolherFicandoBloqueado() throws Exception {
        Cenario cenario = criarCenario("TAC6", "501368006");
        UUID processo = idDe(criar(cenario, null).andReturn().getResponse().getContentAsString());

        submeter(processo, cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path", contains("tipoAtoId")));
        assertThat(processoRepository.findById(processo).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        gravar(processo, jwtComAtos(cenario), cenario.autorizado().getId()).andExpect(status().isOk());
        submeter(processo, cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        // Em correção: volta a EM_SUBMISSAO com a data da primeira submissão.
        jdbcTemplate.update("UPDATE processo SET estado = 'EM_SUBMISSAO' WHERE id = ?", processo);
        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtComAtos(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados", hasItem("tipoAtoId")));
        gravar(processo, jwtSemAtos(cenario), cenario.naoAutorizado().getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[*].path", hasItem("tipoAtoId")));
        assertThat(processoRepository.findById(processo).orElseThrow().getTipoAto().getId())
                .isEqualTo(cenario.autorizado().getId());
    }

    private ResultActions criar(Cenario cenario, Long tipoAtoId) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", cenario.procedimento().getId());
        if (tipoAtoId != null) {
            corpo.put("tipoAtoId", tipoAtoId);
        }
        return mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos").with(jwtComAtos(cenario))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(corpo)));
    }

    /** O rascunho igual ao que a submissão pede, com o tipo de ato indicado. */
    private ResultActions gravar(UUID processo, RequestPostProcessor autenticacao, Long tipoAtoId) throws Exception {
        long versao = processoRepository.findById(processo).orElseThrow().getVersao();
        String corpo = "{\"versao\":" + versao + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\",\"tipoAtoId\":" + tipoAtoId + "}";
        return mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(autenticacao)
                .contentType("application/json").content(corpo));
    }

    private ResultActions submeter(UUID processo, Cenario cenario) throws Exception {
        long versao = processoRepository.findById(processo).orElseThrow().getVersao();
        String corpo = "{\"versao\":" + versao + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + processo + "/submeter").with(jwtComAtos(cenario))
                .contentType("application/json").content(corpo));
    }

    private UUID idDe(String corpo) throws Exception {
        JsonNode json = objectMapper.readTree(corpo);
        return UUID.fromString(json.get("id").asText());
    }

    private static Map<String, Object> atosDe(Cenario cenario) {
        return Map.of("serie_1", List.of(), "serie_2", List.of(Map.of(
                "id", cenario.autorizado().getIncmActTypeIdSerie2(), "nome", cenario.autorizado().getNome(), "extrato", true)));
    }

    private RequestPostProcessor jwtComAtos(Cenario cenario) {
        return jwtDe(cenario, atosDe(cenario), "H");
    }

    private RequestPostProcessor jwtSemAtos(Cenario cenario) {
        return jwtDe(cenario, null, "H");
    }

    private RequestPostProcessor jwtDe(Cenario cenario, Map<String, Object> atos, String letraParte) {
        Map<String, Object> entidade = new HashMap<>(Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(),
                "nif", cenario.entidade().getNipc(), "nome", "Entidade Teste", "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 1234567891234567L, "letra", letraParte, "descricao", "Parte " + letraParte)));
        if (atos != null) {
            entidade.put("atos_autorizados", atos);
        }
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", entidade));
    }

    /** Um Procedimento da 2.ª série H com o formulário da Informação Geral, num Instrumento sem elementos afetados. */
    private Cenario criarCenario(String codigo, String nif) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("TI-" + codigo, "Instrumento " + codigo, "SRUP"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor-" + codigo.toLowerCase() + "@cm-teste.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/TAC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração " + codigo, autor));
        return new Cenario(entidade, autor, instrumento, dinamica, procedimento,
                tipoAtoDaSegundaSerie("AV-" + codigo, "Aviso " + codigo), tipoAtoDaSegundaSerie("ED-" + codigo, "Edital " + codigo));
    }

    private TipoAto tipoAtoDaSegundaSerie(String codigo, String nome) {
        TipoAto tipoAto = new TipoAto(codigo, nome, 1);
        tipoAto.setIncmActTypeIdSerie2(PROXIMO_ACT_TYPE.incrementAndGet());
        return tipoAtoRepository.saveAndFlush(tipoAto);
    }
}
