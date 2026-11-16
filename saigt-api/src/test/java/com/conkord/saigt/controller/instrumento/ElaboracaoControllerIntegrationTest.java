package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.CriarElaboracaoRequest;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.service.instrumento.DinamicaService;
import com.conkord.saigt.service.instrumento.ProcessoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ElaboracaoControllerIntegrationTest {

    private static final String URL_CRIAR = "/api/v1/instrumentos/elaboracao";
    // A base H2 é partilhada pelas suites: códigos únicos por teste.
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9100);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    // Spies: por omissão fazem o trabalho a sério; só os testes do desfazer lhes põem uma falha.
    @SpyBean
    private DinamicaService dinamicaService;

    @SpyBean
    private ProcessoService processoService;

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
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private InstrumentoConcelhoRepository instrumentoConcelhoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SnitReferenciaRepository snitReferenciaRepository;

    @AfterEach
    void esvaziarReplica() {
        snitReferenciaRepository.deleteAll();
    }

    /** O concelho na réplica do SNIT com a região `nut2Code` (nula: sem o campo), em vez do que lá estiver. */
    private void naReplica(String dtcc, String nut2Code) {
        snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, dtcc)
                .ifPresent(snitReferenciaRepository::delete);
        String regiao = nut2Code == null ? "" : ",\"nut2Code\":\"" + nut2Code + "\"";
        snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, "CONCELHO " + dtcc,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"CONCELHO " + dtcc + "\"" + regiao + "}", Instant.now()));
    }

    private void regiaoNaReplica(String codigo) {
        snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.NUT2, codigo, codigo,
                "{\"code\":\"" + codigo + "\",\"label\":\"" + codigo + "\"}", Instant.now()));
    }

    /** Outra região da réplica, com um concelho só do SNIT para entrar na lista de NUT II. */
    private String outraNutII(Cenario cenario) {
        String codigo = "OUTRA " + cenario.nutII();
        regiaoNaReplica(codigo);
        naReplica("O" + cenario.camara().getDico(), codigo);
        return codigo;
    }

    private record Cenario(TipoInstrumento tipoInstrumento, InstrumentoProcedimentoConfig procedimento, TipoAto tipoAto,
                           Entidade camara, String nutII, UUID utilizadorId) {
    }

    private Cenario cenario(String dinamica) {
        return cenario(dinamica, null);
    }

    private Cenario cenario(String dinamica, AmbitoTerritorial ambito) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento novoTipo = new TipoInstrumento("PDM-E" + n, "Plano Diretor Municipal", "PLANO");
        novoTipo.setAmbitoTerritorial(ambito);
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(novoTipo);
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica(dinamica)));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DEC-" + n, "Decisão de elaborar"));
        TipoProcedimento discussao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DISC-" + n, "Discussão pública"));
        InstrumentoProcedimentoConfig primeiro = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig segundo = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, discussao, 2, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, primeiro));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, segundo));

        TipoAto tipoAto = new TipoAto("ATO-E" + n, "Aviso " + n, 1);
        tipoAto.setIncmActTypeIdSerie2((long) n);
        tipoAtoRepository.saveAndFlush(tipoAto);

        Entidade camara = EntidadeTeste.nova("50" + n + "001", "Município de Teste " + n);
        camara.setDico(String.valueOf(n));
        entidadeRepository.saveAndFlush(camara);
        String nutII = "REGIAO DE TESTE " + n;
        regiaoNaReplica(nutII);
        naReplica(camara.getDico(), nutII);
        UUID utilizadorId = utilizadorRepository.saveAndFlush(new Utilizador("Técnica " + n, "tecnica" + n + "@cm.pt", "INCM")).getId();
        return new Cenario(tipoInstrumento, primeiro, tipoAto, camara, nutII, utilizadorId);
    }

    private TipoDinamica tipoDinamica(String codigo) {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> codigo.equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigo, codigo)));
    }

    private RequestPostProcessor jwtDaCamara(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.utilizadorId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.camara().getId(), "nif", cenario.camara().getNipc(), "nome", "Câmara", "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private CriarElaboracaoRequest pedido(Cenario cenario, String designacao) {
        return new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), designacao, cenario.nutII(),
                List.of(cenario.camara().getDico()), cenario.tipoAto().getId());
    }

    /** Uma segunda Câmara, para os pedidos com dois concelhos. */
    private String outroConcelho(Cenario cenario) {
        String dico = String.valueOf(Integer.parseInt(cenario.camara().getDico()) - 1000);
        Entidade camara = EntidadeTeste.nova("51" + dico + "01", "Município de Teste " + dico);
        camara.setDico(dico);
        entidadeRepository.saveAndFlush(camara);
        return dico;
    }

    private ResultActions criar(Cenario cenario, CriarElaboracaoRequest pedido) throws Exception {
        return mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(pedido)));
    }

    /** Instrumentos, Dinâmicas e Processos do tipo do cenário: o que o desfazer tem de deixar a zero. */
    private List<Integer> gravadosDoTipo(Cenario cenario) {
        Long tipoId = cenario.tipoInstrumento().getId();
        return List.of(
                jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento WHERE tipo_instrumento_id = ?", Integer.class, tipoId),
                jdbcTemplate.queryForObject("SELECT count(*) FROM dinamica d JOIN instrumento_dinamica_config c "
                        + "ON c.id = d.instrumento_dinamica_config_id WHERE c.tipo_instrumento_id = ?", Integer.class, tipoId),
                jdbcTemplate.queryForObject("SELECT count(*) FROM processo p JOIN instrumento_procedimento_config pc "
                        + "ON pc.id = p.instrumento_procedimento_config_id JOIN instrumento_dinamica_config c "
                        + "ON c.id = pc.instrumento_dinamica_config_id WHERE c.tipo_instrumento_id = ?", Integer.class, tipoId),
                jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento_concelho ic JOIN instrumento i "
                        + "ON i.id = ic.instrumento_id WHERE i.tipo_instrumento_id = ?", Integer.class, tipoId));
    }

    private List<Instrumento> instrumentosDoTipo(Cenario cenario) {
        return instrumentoRepository.findAll().stream()
                .filter(instrumento -> instrumento.getTipoInstrumento().getId().equals(cenario.tipoInstrumento().getId()))
                .toList();
    }

    @Test
    void deveDevolverOPrimeiroProcedimentoEOTerritorioComOConcelhoDaCamara() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwtDaCamara(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procedimento.instrumentoProcedimentoConfigId").value(cenario.procedimento().getId()))
                .andExpect(jsonPath("$.procedimento.nomeProcedimento").value("Decisão de elaborar"))
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false))
                .andExpect(jsonPath("$.pedeLocalizacao").value("NAO"))
                .andExpect(jsonPath("$.procedimento.tiposAtoDisponiveis[*].id").value(hasItem(cenario.tipoAto().getId().intValue())))
                .andExpect(jsonPath("$.nutsII[*].codigo").value(hasItem(cenario.nutII())))
                .andExpect(jsonPath("$.nutsII[?(@.codigo == '" + cenario.nutII() + "')].nome").value(hasItem(cenario.nutII())))
                .andExpect(jsonPath("$.concelhos[*].codigo").value(hasItem(cenario.camara().getDico())))
                .andExpect(jsonPath("$.concelhos[?(@.codigo == '" + cenario.camara().getDico() + "')].nome")
                        .value(hasItem("Teste " + cenario.camara().getDico())))
                .andExpect(jsonPath("$.concelhoEntidade").value(cenario.camara().getDico()));
    }

    @Test
    void osConcelhosVemDaReplicaComANutIIDoSnitEONomeCuradoQuandoHaCamara() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);
        String soLocal = outroConcelho(cenario);
        String soSnit = "S" + cenario.camara().getDico();
        naReplica(soSnit, null);

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwtDaCamara(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambitoTerritorial").value("MUNICIPAL"))
                .andExpect(jsonPath("$.concelhos[?(@.codigo == '" + cenario.camara().getDico() + "')].nutIICodigo")
                        .value(hasItem(cenario.nutII())))
                .andExpect(jsonPath("$.concelhos[?(@.codigo == '" + cenario.camara().getDico() + "')].nome")
                        .value(hasItem("Teste " + cenario.camara().getDico())))
                .andExpect(jsonPath("$.concelhos[?(@.codigo == '" + soSnit + "')].nome").value(hasItem("CONCELHO " + soSnit)))
                .andExpect(jsonPath("$.concelhos[?(@.codigo == '" + soSnit + "')].nutIICodigo").value(hasItem(nullValue())))
                .andExpect(jsonPath("$.concelhos[*].codigo").value(not(hasItem(soLocal))));
    }

    @Test
    void semReplicaAsListasDoFormularioVemVazias() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);
        snitReferenciaRepository.deleteAll();

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwtDaCamara(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutsII").isEmpty())
                .andExpect(jsonPath("$.concelhos").isEmpty())
                .andExpect(jsonPath("$.concelhoEntidade").value(cenario.camara().getDico()));
    }

    @Test
    void deveDevolver400EmConcelhoSemGravarNadaComUmConcelhoDeOutraNutII() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);
        String outra = outraNutII(cenario);
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste",
                outra, List.of(cenario.camara().getDico()), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("concelho"))
                .andExpect(jsonPath("$.campos[0].erro").value("O concelho não pertence à NUT II escolhida."));

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void deveAceitarOConcelhoDaNutIIEscolhida() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);

        criar(cenario, pedido(cenario, "PDM da NUT certa")).andExpect(status().isCreated());

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);
    }

    @Test
    void deveAceitarUmConcelhoSemNutIIConhecidaComQualquerNutII() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);
        String outra = outraNutII(cenario);
        naReplica(cenario.camara().getDico(), null);
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste",
                outra, List.of(cenario.camara().getDico()), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isCreated());

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);
    }

    @Test
    void naoDeveRecusarConcelhosDeOutraNutIINoTipoIntermunicipal() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.INTERMUNICIPAL);
        String outro = outroConcelho(cenario);
        String outra = outraNutII(cenario);
        naReplica(outro, outra);

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwtDaCamara(cenario)))
                .andExpect(jsonPath("$.ambitoTerritorial").value("INTERMUNICIPAL"));
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDIM de Teste",
                outra, List.of(cenario.camara().getDico(), outro), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isCreated());

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 2);
    }

    @Test
    void deveDevolver404QuandoOTipoNaoTemElaboracao() throws Exception {
        Cenario cenario = cenario("ALTERACAO");

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwtDaCamara(cenario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ELABORACAO_INEXISTENTE"));
    }

    @Test
    void deveDevolver403AUmUtilizadorDaDgt() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        mockMvc.perform(get("/api/v1/tipos-instrumento/" + cenario.tipoInstrumento().getId() + "/elaboracao")
                        .with(jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                                .claim("perfil_interno", "TECNICO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveCriarOIgtAElaboracaoEADecisaoDeElaborarNumSoPedido() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        String designacao = "Plano Diretor Municipal de Teste com uma designação bem comprida";

        String resposta = mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido(cenario, designacao))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processo.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.processo.tipoAtoId").value(cenario.tipoAto().getId()))
                .andReturn().getResponse().getContentAsString();

        JsonNode corpo = objectMapper.readTree(resposta);
        UUID instrumentoId = UUID.fromString(corpo.get("instrumentoId").asText());
        Instrumento instrumento = instrumentoRepository.findById(instrumentoId).orElseThrow();
        assertThat(instrumento.getDesignacao()).isEqualTo(designacao);
        assertThat(instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId))
                .extracting(InstrumentoConcelho::getMunicipioCodigo).containsExactly(cenario.camara().getDico());

        List<Dinamica> dinamicas = dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.values()));
        assertThat(dinamicas).hasSize(1);
        assertThat(dinamicas.get(0).getId().toString()).isEqualTo(corpo.get("dinamicaId").asText());
        assertThat(dinamicas.get(0).getDescritivo()).isEqualTo(designacao.substring(0, 50).strip());
    }

    @Test
    void deveCriarOProcessoNaEntidadeDaSessaoEOInstrumentoSemEntidade() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        String resposta = mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido(cenario, "PDM de Sintra"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID processoId = UUID.fromString(objectMapper.readTree(resposta).get("processo").get("id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT entidade_id FROM processo WHERE id = ?", Long.class, processoId))
                .isEqualTo(cenario.camara().getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'instrumento' AND column_name = 'entidade_id'", Integer.class)).isZero();
    }

    @Test
    void naoDeveGravarNadaComUmConcelhoInexistente() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste",
                cenario.nutII(), List.of("0000"), cenario.tipoAto().getId());

        mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("concelhos"))
                .andExpect(jsonPath("$.campos[0].erro").value("Concelho inexistente."));

        assertThat(instrumentosDoTipo(cenario)).isEmpty();
    }

    @Test
    void naoDeveGravarNadaComUmTipoDeAtoForaDoProcedimento() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        TipoAto primeiraSerie = new TipoAto("ATO-1S-" + cenario.tipoAto().getId(), "Ato da 1.ª série", 1);
        primeiraSerie.setIncmActTypeIdSerie1(cenario.tipoAto().getId() + 50_000);
        tipoAtoRepository.saveAndFlush(primeiraSerie);
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste",
                cenario.nutII(), List.of(cenario.camara().getDico()), primeiraSerie.getId());

        mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoAtoId"));

        assertThat(instrumentosDoTipo(cenario)).isEmpty();
    }

    @Test
    void deveDevolver400SemDesignacao() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido(cenario, " "))))
                .andExpect(status().isBadRequest());

        assertThat(instrumentosDoTipo(cenario)).isEmpty();
    }

    @Test
    void deveGravarAAuditoriaDasTresCriacoesCadaUmaComASuaEntrada() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        String resposta = criar(cenario, pedido(cenario, "PDM de Auditoria")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode corpo = objectMapper.readTree(resposta);
        List<String> ocorrencias = List.of(corpo.get("instrumentoId").asText(), corpo.get("dinamicaId").asText(),
                corpo.get("processo").get("id").asText());
        for (String id : ocorrencias) {
            assertThat(jdbcTemplate.queryForList("SELECT acao FROM auditoria WHERE id_ocorrencia = ?", String.class, id))
                    .containsExactly("CRIACAO");
        }
        assertThat(jdbcTemplate.queryForList("SELECT tipo_ocorrencia FROM auditoria WHERE id_ocorrencia IN (?, ?, ?)",
                String.class, ocorrencias.toArray())).containsExactlyInAnyOrder("INSTRUMENTO", "DINAMICA", "PROCESSO");
    }

    @Test
    void deveAceitarOPedidoSemNutIIEComUmSoConcelho() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM sem NUT II", null,
                List.of(cenario.camara().getDico()), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isCreated());

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);
    }

    @Test
    void deveAceitarOFormatoAnteriorComUmSoConcelho() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        Map<String, Object> corpo = Map.of("tipoInstrumentoId", cenario.tipoInstrumento().getId(), "designacao", "PDM antigo",
                "nutIICodigo", cenario.nutII(), "concelho", cenario.camara().getDico(),
                "tipoAtoId", cenario.tipoAto().getId());

        mockMvc.perform(post(URL_CRIAR).with(jwtDaCamara(cenario)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated());

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);
    }

    @Test
    void deveDevolver400ComUmaNutIIInexistenteSemGravarNada() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste", "ZZZ",
                List.of(cenario.camara().getDico()), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("nutIICodigo"));

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void deveDevolver409ComADesignacaoRepetidaNoMesmoTipoEConcelhoSemGravarMais() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        criar(cenario, pedido(cenario, "PDM de Sintra")).andExpect(status().isCreated());
        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);

        criar(cenario, pedido(cenario, " pdm de sintra "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DESIGNACAO_DUPLICADA"))
                .andExpect(jsonPath("$.campos[0].path").value("designacao"));

        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 1);
    }

    @Test
    void deveDevolver400ComUmConcelhoRepetidoSemGravarNada() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.INTERMUNICIPAL);
        String dico = cenario.camara().getDico();
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDIM de Teste",
                cenario.nutII(), List.of(dico, dico), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("concelhos"));

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void deveExigirDoisOuMaisConcelhosNoTipoIntermunicipalEGravarTodos() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.INTERMUNICIPAL);
        CriarElaboracaoRequest umSo = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDIM de Teste",
                cenario.nutII(), List.of(cenario.camara().getDico()), cenario.tipoAto().getId());

        criar(cenario, umSo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("concelhos"));
        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);

        String outro = outroConcelho(cenario);
        naReplica(outro, cenario.nutII());
        CriarElaboracaoRequest dois = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDIM de Teste",
                cenario.nutII(), List.of(cenario.camara().getDico(), outro), cenario.tipoAto().getId());

        criar(cenario, dois).andExpect(status().isCreated());
        assertThat(gravadosDoTipo(cenario)).containsExactly(1, 1, 1, 2);
    }

    @Test
    void deveDevolver400ComVariosConcelhosNoTipoMunicipalSemGravarNada() throws Exception {
        Cenario cenario = cenario("ELABORACAO", AmbitoTerritorial.MUNICIPAL);
        String outro = outroConcelho(cenario);
        naReplica(outro, cenario.nutII());
        CriarElaboracaoRequest pedido = new CriarElaboracaoRequest(cenario.tipoInstrumento().getId(), "PDM de Teste",
                cenario.nutII(), List.of(cenario.camara().getDico(), outro), cenario.tipoAto().getId());

        criar(cenario, pedido).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("concelhos"));

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void deveDevolver403AUmUtilizadorDaDgtAoCriar() throws Exception {
        Cenario cenario = cenario("ELABORACAO");

        mockMvc.perform(post(URL_CRIAR)
                        .with(jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                                .claim("perfil_interno", "TECNICO")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido(cenario, "PDM de Teste"))))
                .andExpect(status().isForbidden());

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    // O desfazer, passo a passo: o 1.º passo (Instrumento) falha pela designação repetida, no teste de cima;
    // o 2.º e o 3.º falham aqui depois de o Instrumento (e a Dinâmica) já estarem gravados na transação.

    @Test
    void deveDesfazerOInstrumentoQuandoACriacaoDaDinamicaFalha() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        doThrow(new IllegalStateException("falha ao iniciar a Dinâmica")).when(dinamicaService).iniciar(any(), any());

        criar(cenario, pedido(cenario, "PDM de Teste")).andExpect(status().isInternalServerError());

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void deveDesfazerOInstrumentoEADinamicaQuandoACriacaoDoProcessoFalha() throws Exception {
        Cenario cenario = cenario("ELABORACAO");
        doThrow(new IllegalStateException("falha ao criar o Processo")).when(processoService).criar(any(), any());

        criar(cenario, pedido(cenario, "PDM de Teste")).andExpect(status().isInternalServerError());

        assertThat(gravadosDoTipo(cenario)).containsExactly(0, 0, 0, 0);
    }
}
