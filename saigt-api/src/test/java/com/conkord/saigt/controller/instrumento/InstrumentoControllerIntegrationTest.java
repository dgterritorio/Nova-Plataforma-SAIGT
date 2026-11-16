package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.dto.instrumento.CriarInstrumentoRequest;
import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.dto.instrumento.DevolverDgtRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.ReatribuirAutorRequest;
import com.conkord.saigt.dto.instrumento.RegistoExternoProcessoRequest;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import com.conkord.saigt.integracao.snit.MunicipioIgtSnit;
import com.conkord.saigt.integracao.snit.IgtSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.integracao.snit.PaginaSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstrumentoControllerIntegrationTest {

    private static final String URL = "/api/v1/instrumentos";
    private static final List<String> SINTRA = List.of("1111");
    private static final List<String> CASCAIS = List.of("1105");
    private static final String INFORMACAO_BASE_COMPLETA = "{"
            + "\"deliberacao\":{\"prazoAnos\":1,\"prazoMeses\":2,\"prazoDias\":3,\"avaliacaoAmbiental\":true,"
            + "\"dataDeliberacao\":\"2026-09-01\",\"textoPublicar\":\"Texto a publicar.\"},"
            + "\"textoAto\":{\"prazoAnos\":4,\"prazoMeses\":5,\"prazoDias\":6,\"avaliacaoAmbiental\":false}}";

    @Autowired
    private InstrumentoConcelhoRepository instrumentoConcelhoRepository;

    @Autowired
    private SnitReferenciaRepository snitReferenciaRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CondicaoRepository condicaoRepository;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ListaValoresRepository listaValoresRepository;

    @Autowired
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProcessoInformacaoBaseRepository processoInformacaoBaseRepository;

    @Autowired
    private ProcessoValidacaoRejeicaoRepository processoValidacaoRejeicaoRepository;

    private TipoInstrumento criarTipoInstrumentoComPermissaoParaEntidade(String codigo) {
        return criarCadeiaComPermissaoParaEntidade(codigo).tipoInstrumento();
    }

    private record CadeiaConfigurada(TipoInstrumento tipoInstrumento, InstrumentoDinamicaConfig dinamicaConfig,
                                      InstrumentoProcedimentoConfig procedimentoConfig) {
    }

    private CadeiaConfigurada criarCadeiaComPermissaoParaEntidade(String codigo) {
        return criarCadeiaComPermissaoParaEntidade(codigo, "SEGUNDA_H");
    }

    private CadeiaConfigurada criarCadeiaComPermissaoParaEntidade(String codigo, String serieDr) {
        return criarCadeiaComPermissaoParaEntidade(codigo, serieDr,
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo)));
    }

    private CadeiaConfigurada criarCadeiaComPermissaoParaEntidade(String codigo, String serieDr, TipoProcedimento tipoProcedimento) {
        return criarCadeiaComPermissaoParaEntidade(codigo, "PLANO", serieDr, tipoProcedimento);
    }

    private CadeiaConfigurada criarCadeiaComPermissaoParaEntidade(String codigo, String categoria, String serieDr,
                                                                  TipoProcedimento tipoProcedimento) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", categoria));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, serieDr));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        return new CadeiaConfigurada(tipoInstrumento, dinamicaConfig, procedimentoConfig);
    }

    /** A claim `entidade.id` identifica a entidade da sessão; sem entidade com este NIPC fica de fora. */
    private Map<String, Object> comId(String nif, Map<String, Object> claims) {
        Map<String, Object> comId = new java.util.HashMap<>(claims);
        entidadeRepository.findAllByNipc(nif).stream().findFirst().ifPresent(entidade -> comId.put("id", entidade.getId()));
        return comId;
    }

    /** O `sub` é aleatório: só serve pedidos que não resolvem o utilizador. */
    private RequestPostProcessor jwtComEntidade(String nif) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "CM", "nif", nif, "nome", "Entidade Teste", "pcm", false))));
    }

    /** O `sub` tem de existir em `utilizador`; `incm_enrollment_id` e `dr_parte.id` são exigidos na submissão de 2.ª série. */
    private RequestPostProcessor jwtComEntidadeEUtilizadorReal(String nif, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "CM", "nif", nif, "nome", "Entidade Teste", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H")))));
    }

    private RequestPostProcessor jwtDeAutorComAtosAutorizados(String nif, UUID utilizadorId, long idTipoAto, boolean permiteExtrato) {
        Map<String, Object> atosAutorizados = Map.of("serie_1", List.of(),
                "serie_2", List.of(Map.of("id", idTipoAto, "nome", "Ato " + idTipoAto, "extrato", permiteExtrato)));
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "CM", "nif", nif, "nome", "Entidade Teste", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"),
                        "atos_autorizados", atosAutorizados))));
    }

    private RequestPostProcessor jwtComAtosAutorizados(String nif, UUID utilizadorId, Map<String, Object> atosAutorizados) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "CM", "nif", nif, "nome", "Entidade Teste", "pcm", false, "atos_autorizados", atosAutorizados))));
    }

    /** Sem claim `entidade`: a visibilidade decorre de `perfil_interno`. */
    private RequestPostProcessor jwtComPerfilInterno(UUID utilizadorId, String perfilInterno) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfilInterno));
    }

    /** O ator PCM resulta de `entidade.pcm = true`, não de um perfil. */
    private RequestPostProcessor jwtComEntidadePcm(String nif) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "PCM", "nif", nif, "nome", "Entidade PCM", "pcm", true))));
    }

    private UUID criarUtilizadorIncm(String email) {
        return utilizadorRepository.saveAndFlush(new Utilizador("Utilizador Teste", email, "INCM")).getId();
    }

    /** Utilizador INCM com vínculo à entidade do processo, como o deixa o login (#167). */
    private UUID criarUtilizadorDaEntidade(String email, Processo processo) {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador Teste", email, "INCM"));
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, processo.getEntidade(), Instant.now()));
        return utilizador.getId();
    }

    /** Os concelhos que se podem indicar são o DICO das Câmaras; a base de testes não tem a carga das entidades. */
    @BeforeEach
    void garantirCamarasDosConcelhos() {
        List<String> existentes = entidadeRepository.findByTipoEntidadeAndDicoIsNotNullAndAtivoTrue(TipoEntidade.CM).stream()
                .map(Entidade::getDico).toList();
        for (String dico : List.of("1111", "1105")) {
            if (!existentes.contains(dico)) {
                Entidade camara = EntidadeTeste.nova("50999" + dico, "Município de Teste " + dico);
                camara.setDico(dico);
                entidadeRepository.saveAndFlush(camara);
            }
        }
    }

    /** Tem de existir: o registo externo grava-o em `criado_por`. */
    /** `tipoEntidade` nulo deixa a claim sem `tipo_entidade`, como num token de uma sessão sem tipo. */
    private RequestPostProcessor jwtComTipoEntidade(String nif, UUID utilizadorId, String tipoEntidade) {
        Map<String, Object> entidade = new java.util.HashMap<>(comId(nif, Map.of("nif", nif, "nome", "Entidade Teste", "pcm", false,
                "incm_enrollment_id", "2015482", "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
        if (tipoEntidade != null) {
            entidade.put("tipo_entidade", tipoEntidade);
        }
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM").claim("entidade", entidade));
    }

    private CadeiaConfigurada criarCadeiaComPermissaoParaTipo(String codigo, TipoEntidade tipoEntidade) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig,
                        tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo)),
                        1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(tipoEntidade, procedimentoConfig));
        return new CadeiaConfigurada(tipoInstrumento, dinamicaConfig, procedimentoConfig);
    }

    private UUID criarUtilizadorMaster(String email) {
        Utilizador admin = new Utilizador("Admin DGT", email, "AD_DGT");
        admin.setPerfilInterno(PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(admin).getId();
    }

    @Test
    void deveDevolver201AoCriarInstrumentoComPermissao() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-201");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000201", "Câmara 201"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador201@cm-201.pt");
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM de Lisboa", SINTRA);

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000201", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value("PDM de Lisboa"))
                .andExpect(jsonPath("$.numero").isString())
                .andExpect(jsonPath("$.referencia").doesNotExist())
                .andExpect(jsonPath("$.numeroSequencial").doesNotExist())
                .andExpect(jsonPath("$.emVigor").value(false))
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.concelhos", contains("1111")))
                .andExpect(jsonPath("$.origemDados").value("NATIVO"));
    }

    @Test
    void deveAceitarAMesmaDesignacaoEmConcelhosDiferentesComNumerosDiferentes() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-409");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000409", "Câmara 409"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador409@cm-409.pt");
        CriarInstrumentoRequest emSintra = new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM de Lisboa", SINTRA);
        CriarInstrumentoRequest emCascais = new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM de Lisboa", CASCAIS);

        String primeiro = mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000409", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emSintra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String segundo = mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000409", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emCascais)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value("PDM de Lisboa"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(segundo).get("numero").asText())
                .isNotEqualTo(objectMapper.readTree(primeiro).get("numero").asText());
        UUID instrumentoId = UUID.fromString(objectMapper.readTree(primeiro).get("id").asText());
        assertThat(instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId))
                .extracting(InstrumentoConcelho::getMunicipioCodigo).containsExactly("1111");
    }

    @Test
    void deveDevolver409AoCriarInstrumentoComADesignacaoDeOutroDoMesmoTipoNoMesmoConcelho() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-DUP");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000419", "Câmara DUP"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-dup@cm-dup.pt");
        CriarInstrumentoRequest primeiro = new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM de Sintra", SINTRA);
        CriarInstrumentoRequest repetido = new CriarInstrumentoRequest(tipoInstrumento.getId(), " pdm de sintra ",
                List.of("1105", "1111"));

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000419", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(primeiro)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000419", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(repetido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DESIGNACAO_DUPLICADA"))
                .andExpect(jsonPath("$.campos[0].path").value("designacao"));
    }

    private record ClassificacaoCriada(ListaValores dominio, ListaValores classe, ListaValores tipologia) {
    }

    private ClassificacaoCriada criarClassificacaoSrup(String sufixo) {
        ListaValores dominio = listaValoresRepository.saveAndFlush(
                new ListaValores("DOMINIO_SRUP", "D" + sufixo, "Domínio " + sufixo, 1));
        ListaValores classe = listaValoresRepository.saveAndFlush(
                new ListaValores("CLASSE_SRUP", "C" + sufixo, "Classe " + sufixo, 1, dominio));
        ListaValores tipologia = listaValoresRepository.saveAndFlush(
                new ListaValores("TIPOLOGIA_SRUP", "T" + sufixo, "Tipologia " + sufixo, 1, classe));
        return new ClassificacaoCriada(dominio, classe, tipologia);
    }

    private TipoInstrumento criarTipoSrup(String codigo) {
        return criarCadeiaComPermissaoParaEntidade(codigo, "SRUP", "SEGUNDA_H",
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo)))
                .tipoInstrumento();
    }

    private ResultActions criarInstrumentoComTipologia(String nif, UUID utilizadorId, Long tipoInstrumentoId,
                                                       String designacao, Long tipologiaSrupId) throws Exception {
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumentoId, designacao, null, SINTRA,
                tipologiaSrupId);
        return mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal(nif, utilizadorId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(pedido)));
    }

    @Test
    void deveCriarInstrumentoSrupComTipologiaEDevolverATipologiaAClasseEODominio() throws Exception {
        TipoInstrumento tipoSrup = criarTipoSrup("SRUP-T1");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000731", "Câmara SRUP-T1"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-t1@cm.pt");
        ClassificacaoCriada classificacao = criarClassificacaoSrup("T1");

        criarInstrumentoComTipologia("501000731", utilizadorId, tipoSrup.getId(), "Servidão T1",
                classificacao.tipologia().getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classificacaoSrup.tipologia.id").value(classificacao.tipologia().getId()))
                .andExpect(jsonPath("$.classificacaoSrup.tipologia.descricao").value("Tipologia T1"))
                .andExpect(jsonPath("$.classificacaoSrup.classe.descricao").value("Classe T1"))
                .andExpect(jsonPath("$.classificacaoSrup.dominio.descricao").value("Domínio T1"));
    }

    @Test
    void deveMostrarAClassificacaoNoPainelENaListagemDoInstrumentoSrup() throws Exception {
        TipoInstrumento tipoSrup = criarTipoSrup("SRUP-T2");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000732", "Câmara SRUP-T2"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-t2@cm.pt");
        ClassificacaoCriada classificacao = criarClassificacaoSrup("T2");
        Utilizador criadoPor = utilizadorRepository.findById(utilizadorId).orElseThrow();
        Instrumento instrumento = new Instrumento(tipoSrup, "2", "Servidão T2", criadoPor, criadoPor);
        instrumento.setTipologiaSrup(classificacao.tipologia());
        instrumento = instrumentoRepository.saveAndFlush(instrumento);

        mockMvc.perform(get(URL + "/" + instrumento.getId()).with(jwtComEntidade("501000732")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacaoSrup.tipologia.descricao").value("Tipologia T2"))
                .andExpect(jsonPath("$.classificacaoSrup.classe.descricao").value("Classe T2"))
                .andExpect(jsonPath("$.classificacaoSrup.dominio.descricao").value("Domínio T2"));
        mockMvc.perform(get(URL).param("size", "100").with(jwtComEntidade("501000732")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.designacao == 'Servidão T2')].classificacaoSrup.dominio.descricao",
                        contains("Domínio T2")));
    }

    @Test
    void deveDevolverClassificacaoNulaQuandoOInstrumentoNaoTemTipologia() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-T3");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000733", "Câmara T3"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t3@cm.pt");

        criarInstrumentoComTipologia("501000733", utilizadorId, tipoInstrumento.getId(), "PDM T3", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classificacaoSrup").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveDevolver400QuandoSeEnviaTipologiaNumTipoQueNaoESrup() throws Exception {
        TipoInstrumento pdm = criarTipoInstrumentoComPermissaoParaEntidade("PDM-T4");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000734", "Câmara T4"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t4@cm.pt");
        ClassificacaoCriada classificacao = criarClassificacaoSrup("T4");

        criarInstrumentoComTipologia("501000734", utilizadorId, pdm.getId(), "PDM T4", classificacao.tipologia().getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipologiaSrupId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Campo não aplicável a este tipo de Instrumento."));
    }

    @Test
    void deveDevolver400QuandoATipologiaNaoExisteNaoEUmaTipologiaOuEstaInativa() throws Exception {
        TipoInstrumento tipoSrup = criarTipoSrup("SRUP-T5");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000735", "Câmara SRUP-T5"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-t5@cm.pt");
        ClassificacaoCriada classificacao = criarClassificacaoSrup("T5");
        ListaValores inativa = new ListaValores("TIPOLOGIA_SRUP", "T5I", "Tipologia inativa", 2, classificacao.classe());
        inativa.setAtivo(false);
        inativa = listaValoresRepository.saveAndFlush(inativa);

        for (Long id : List.of(Long.MAX_VALUE, classificacao.classe().getId(), inativa.getId())) {
            criarInstrumentoComTipologia("501000735", utilizadorId, tipoSrup.getId(), "Servidão T5 " + id, id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].path").value("tipologiaSrupId"))
                    .andExpect(jsonPath("$.campos[0].erro").value("Tipologia inexistente ou inativa."));
        }
    }

    @Test
    void deveDevolver400AoCriarInstrumentoSemConcelhoOuComConcelhoInexistente() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-CON");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000429", "Câmara CON"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-con@cm-con.pt");

        for (List<String> concelhos : Arrays.asList(null, List.<String>of(), List.of("9999"))) {
            CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM CON", concelhos);
            mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000429", utilizadorId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(pedido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].path").value("concelhos"));
        }
        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000429", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(tipoInstrumento.getId(), "PDM CON", List.of("Sintra")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value(startsWith("concelhos")));
    }

    @Test
    void deveListarOsConcelhosQueSePodemIndicarAoCriarUmInstrumento() throws Exception {
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000439", "Câmara LST"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-lst@cm-lst.pt");

        mockMvc.perform(get(URL + "/concelhos").with(jwtComEntidadeEUtilizadorReal("501000439", utilizadorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == '1111')].nome").value(hasItem("Teste 1111")));
    }

    @Test
    void deveDevolver400AoCriarInstrumentoSemDesignacao() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-400");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000400", "Câmara 400"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador400@cm-400.pt");
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumento.getId(), null, SINTRA);

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000400", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("designacao"));
    }

    @Test
    void deveAceitarDesignacaoDe255CaracteresERecusarDe256() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-D1");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000731", "Câmara D1"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-d1@cm-d1.pt");
        String designacaoMaxima = "x".repeat(255);

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000731", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(tipoInstrumento.getId(), designacaoMaxima, SINTRA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value(designacaoMaxima));
        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000731", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(tipoInstrumento.getId(), designacaoMaxima + "x", SINTRA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("designacao"));
    }

    @Test
    void deveGuardarSemCortarADesignacaoDoSnitComMaisDe50Caracteres() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-D2");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000732", "Câmara D2"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-d2@cm-d2.pt");
        String designacao = "Plano Diretor Municipal de Vila Nova de Santo André e Arredores (SNIT)";
        when(clienteSnit.obter(987201L)).thenReturn(Optional.of(new IgtSnit(987201L, "PDM-D2", designacao, "in_force")));

        assertThat(designacao).hasSizeGreaterThan(50);
        criarInstrumentoDoSnit("501000732", utilizadorId, tipoInstrumento.getId(), "987201")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value(designacao));
    }

    private ResultActions criarComOrigem(String nif, UUID utilizadorId, Long tipoInstrumentoId, String designacao,
                                         UUID instrumentoOrigemId) throws Exception {
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumentoId, designacao, null, SINTRA, null,
                instrumentoOrigemId);
        return mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal(nif, utilizadorId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(pedido)));
    }

    private Instrumento instrumentoGravado(TipoInstrumento tipo, String designacao, UUID utilizadorId) {
        Utilizador criadoPor = utilizadorRepository.findById(utilizadorId).orElseThrow();
        return instrumentoRepository.saveAndFlush(new Instrumento(tipo, "OR-" + designacao.hashCode(), designacao, criadoPor, criadoPor));
    }

    private UUID origemGravada(UUID instrumentoId) {
        return jdbcTemplate.queryForObject("SELECT instrumento_origem_id FROM instrumento WHERE id = ?", UUID.class, instrumentoId);
    }

    @Test
    void deveCriarUmaServidaoComAServidaoDeOrigemEDevolverAReferenciaGravarAColunaEAuditar() throws Exception {
        TipoInstrumento servidao = criarTipoSrup("SRUP-OR1");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000751", "Câmara SRUP-OR1"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-or1@cm.pt");
        Instrumento origem = instrumentoGravado(criarTipoSrup("SRUP-OR1M"), "Imóvel classificado OR1", utilizadorId);

        String corpo = criarComOrigem("501000751", utilizadorId, servidao.getId(), "Zona de proteção OR1", origem.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoOrigem.id").value(origem.getId().toString()))
                .andExpect(jsonPath("$.instrumentoOrigem.designacao").value("Imóvel classificado OR1"))
                .andReturn().getResponse().getContentAsString();

        UUID criadoId = UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
        assertThat(origemGravada(criadoId)).isEqualTo(origem.getId());
        Auditoria registo = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("INSTRUMENTO", criadoId.toString())
                .stream().filter(linha -> "CRIACAO".equals(linha.getAcao())).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).get("instrumentoOrigemId").asText())
                .isEqualTo(origem.getId().toString());
    }

    @Test
    void deveDevolver400ENaoCriarQuandoUmPlanoIndicaServidaoDeOrigem() throws Exception {
        TipoInstrumento pdm = criarTipoInstrumentoComPermissaoParaEntidade("PDM-OR2");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000752", "Câmara OR2"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-or2@cm.pt");
        Instrumento origem = instrumentoGravado(criarTipoSrup("SRUP-OR2M"), "Imóvel classificado OR2", utilizadorId);

        criarComOrigem("501000752", utilizadorId, pdm.getId(), "PDM OR2", origem.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoOrigemId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Só uma servidão pode indicar uma servidão de origem."));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM instrumento WHERE designacao = 'PDM OR2'", Integer.class))
                .isZero();
    }

    @Test
    void deveDarAMesma400ParaUmaOrigemInexistenteEParaUmaOrigemQueEUmPlano() throws Exception {
        TipoInstrumento servidao = criarTipoSrup("SRUP-OR3");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000753", "Câmara SRUP-OR3"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-or3@cm.pt");
        Instrumento plano = instrumentoGravado(criarTipoInstrumentoComPermissaoParaEntidade("PDM-OR3M"), "PDM OR3", utilizadorId);

        List<String> respostas = new ArrayList<>();
        for (UUID origemId : List.of(UUID.randomUUID(), plano.getId())) {
            String corpo = criarComOrigem("501000753", utilizadorId, servidao.getId(), "Zona de proteção OR3", origemId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("instrumentoOrigemId"))
                    .andExpect(jsonPath("$.campos[0].erro").value("Servidão de origem inválida."))
                    .andReturn().getResponse().getContentAsString();
            JsonNode erro = objectMapper.readTree(corpo);
            respostas.add(erro.get("codigo").asText() + "|" + erro.get("mensagem").asText() + "|" + erro.get("campos"));
        }

        assertThat(respostas.get(0)).isEqualTo(respostas.get(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM instrumento WHERE designacao = 'Zona de proteção OR3'", Integer.class)).isZero();
    }

    @Test
    void deveCriarSemServidaoDeOrigemComoHojeEDevolverAReferenciaNula() throws Exception {
        TipoInstrumento servidao = criarTipoSrup("SRUP-OR4");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000754", "Câmara SRUP-OR4"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-or4@cm.pt");

        String corpo = criarComOrigem("501000754", utilizadorId, servidao.getId(), "Servidão OR4", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoOrigem").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();

        UUID criadoId = UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
        assertThat(origemGravada(criadoId)).isNull();
        Auditoria registo = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("INSTRUMENTO", criadoId.toString())
                .stream().filter(linha -> "CRIACAO".equals(linha.getAcao())).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).has("instrumentoOrigemId")).isFalse();
    }

    @Test
    void deveDevolver403ComoHojeSemValidarAServidaoDeOrigem() throws Exception {
        TipoInstrumento servidaoSemPermissao = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("SRUP-OR5", "Servidão sem permissão", "SRUP"));
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000755", "Câmara SRUP-OR5"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-or5@cm.pt");

        criarComOrigem("501000755", utilizadorId, servidaoSemPermissao.getId(), "Zona de proteção OR5", UUID.randomUUID())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveMostrarAServidaoDeOrigemNoPainelENaListagemENuloNumPlano() throws Exception {
        TipoInstrumento servidao = criarTipoSrup("SRUP-OR6");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000756", "Câmara SRUP-OR6"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-srup-or6@cm.pt");
        Instrumento origem = instrumentoGravado(criarTipoSrup("SRUP-OR6M"), "Imóvel classificado OR6", utilizadorId);
        String corpo = criarComOrigem("501000756", utilizadorId, servidao.getId(), "Zona de proteção OR6", origem.getId())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String criadoId = objectMapper.readTree(corpo).get("id").asText();
        Instrumento plano = instrumentoGravado(criarTipoInstrumentoComPermissaoParaEntidade("PDM-OR6"), "PDM OR6", utilizadorId);

        mockMvc.perform(get(URL + "/" + criadoId).with(jwtComEntidade("501000756")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentoOrigem.id").value(origem.getId().toString()))
                .andExpect(jsonPath("$.instrumentoOrigem.designacao").value("Imóvel classificado OR6"));
        mockMvc.perform(get(URL + "/" + plano.getId()).with(jwtComEntidade("501000756")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentoOrigem").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get(URL).param("size", "100").with(jwtComEntidade("501000756")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.designacao == 'Zona de proteção OR6')].instrumentoOrigem.designacao",
                        contains("Imóvel classificado OR6")));
        mockMvc.perform(get(URL).param("designacao", "Zona de proteção OR6").with(jwtComEntidade("501000756")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].instrumentoOrigem.id").value(origem.getId().toString()));
    }

    @Test
    void deveDevolver403AoCriarInstrumentoDeTipoSemPermissaoAcaoParaOAtor() throws Exception {
        TipoInstrumento tipoInstrumentoSemPermissao = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM-403", "Plano Diretor Municipal", "PLANO"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000403", "Câmara 403"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador403@cm-403.pt");
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumentoSemPermissao.getId(), "2026/PDM/403", SINTRA);

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000403", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    private ResultActions criarInstrumentoDoSnit(String nif, UUID utilizadorId, Long tipoInstrumentoId, String idIgtSnit)
            throws Exception {
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(tipoInstrumentoId, "Designação do pedido", idIgtSnit);
        return mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal(nif, utilizadorId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(pedido)));
    }

    @Test
    void deveCriarInstrumentoDeIgtDoSnitComTipoEDesignacaoDoSnit() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN1");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000711", "Câmara SN1"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn1@cm-sn1.pt");
        when(clienteSnit.obter(987101L)).thenReturn(Optional.of(new IgtSnit(987101L, "PDM-SN1", "PDM de Sintra (SNIT)",
                "in_force", List.of(new MunicipioIgtSnit("1111", "Sintra")))));

        criarInstrumentoDoSnit("501000711", utilizadorId, tipoInstrumento.getId(), "987101")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value("PDM de Sintra (SNIT)"))
                .andExpect(jsonPath("$.tipoInstrumentoId").value(tipoInstrumento.getId()))
                .andExpect(jsonPath("$.concelhos", contains("1111")))
                .andExpect(jsonPath("$.emVigor").value(true));

        List<Instrumento> adotados = instrumentoRepository.findAllByIdIgtSnitIn(List.of("987101"));
        assertThat(adotados).hasSize(1);
        assertThat(instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(adotados.getFirst().getId()))
                .extracting(InstrumentoConcelho::getMunicipioCodigo).containsExactly("1111");
    }

    @Test
    void naoDeveSegurarLigacaoABaseDeDadosDuranteAConsultaAoSnit() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN50");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000791", "Câmara SN50"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn50@cm-sn50.pt");
        HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
        List<Integer> ligacoesAtivasNaConsulta = new java.util.ArrayList<>();
        List<Boolean> transacaoNaConsulta = new java.util.ArrayList<>();
        when(clienteSnit.obter(987113L)).thenAnswer(inv -> {
            ligacoesAtivasNaConsulta.add(pool.getActiveConnections());
            transacaoNaConsulta.add(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(new IgtSnit(987113L, "PDM-SN50", "PDM lento (SNIT)", "in_force",
                    List.of(new MunicipioIgtSnit("1111", "Sintra"))));
        });

        criarInstrumentoDoSnit("501000791", utilizadorId, tipoInstrumento.getId(), "987113")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designacao").value("PDM lento (SNIT)"));

        assertThat(ligacoesAtivasNaConsulta).containsExactly(0);
        assertThat(transacaoNaConsulta).containsExactly(false);
    }

    @Test
    void deveDevolver409AoCriarInstrumentoDeIgtDoSnitQueJaExisteNaSaigt() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN2");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000712", "Câmara SN2"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn2@cm-sn2.pt");
        when(clienteSnit.obter(987102L)).thenReturn(Optional.of(new IgtSnit(987102L, "PDM-SN2", "PDM de Cascais (SNIT)", "in_force")));

        criarInstrumentoDoSnit("501000712", utilizadorId, tipoInstrumento.getId(), "987102").andExpect(status().isCreated());
        criarInstrumentoDoSnit("501000712", utilizadorId, tipoInstrumento.getId(), "987102")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_DUPLICADA"));
    }

    @Test
    void deveDevolver400AoCriarInstrumentoDeIgtDoSnitDeOutroTipo() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN3");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000713", "Câmara SN3"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn3@cm-sn3.pt");
        when(clienteSnit.obter(987103L)).thenReturn(Optional.of(new IgtSnit(987103L, "PU", "PU da Zona Industrial (SNIT)", "in_force")));

        criarInstrumentoDoSnit("501000713", utilizadorId, tipoInstrumento.getId(), "987103")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInstrumentoId"));
        assertThat(instrumentoRepository.existsByIdIgtSnit("987103")).isFalse();
    }

    @Test
    void deveDevolver400AoCriarInstrumentoDeIgtDoSnitQueNaoEstaEmVigor() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN4");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000714", "Câmara SN4"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn4@cm-sn4.pt");
        when(clienteSnit.obter(987104L)).thenReturn(Optional.of(new IgtSnit(987104L, "PDM-SN4", "PDM revogado (SNIT)", "revoked")));

        criarInstrumentoDoSnit("501000714", utilizadorId, tipoInstrumento.getId(), "987104")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("idIgtSnit"));
    }

    @Test
    void deveDevolver404AoCriarInstrumentoDeIgtQueOSnitNaoConhece() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN5");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000715", "Câmara SN5"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn5@cm-sn5.pt");
        when(clienteSnit.obter(987105L)).thenReturn(Optional.empty());

        criarInstrumentoDoSnit("501000715", utilizadorId, tipoInstrumento.getId(), "987105")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver500AoCriarInstrumentoDeIgtComOSnitIndisponivel() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN6");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000716", "Câmara SN6"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn6@cm-sn6.pt");
        when(clienteSnit.obter(987106L)).thenThrow(new SnitIndisponivelException("Serviço do SNIT indisponível."));

        criarInstrumentoDoSnit("501000716", utilizadorId, tipoInstrumento.getId(), "987106")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("INTEGRACAO_INDISPONIVEL"));
    }

    @Test
    void deveDevolver400AoCriarInstrumentoComIdentificadorDoIgtNaoNumerico() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN7");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000717", "Câmara SN7"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn7@cm-sn7.pt");

        criarInstrumentoDoSnit("501000717", utilizadorId, tipoInstrumento.getId(), "1001/../x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("idIgtSnit"));
        verify(clienteSnit, never()).obter(anyLong());
    }

    @Test
    void deveDevolver403SemConsultarOSnitQuandoOTipoNaoTemPermissaoParaOAtor() throws Exception {
        TipoInstrumento semPermissao = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM-SN8", "Plano Diretor Municipal", "PLANO"));
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000718", "Câmara SN8"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn8@cm-sn8.pt");

        criarInstrumentoDoSnit("501000718", utilizadorId, semPermissao.getId(), "987108")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        verify(clienteSnit, never()).obter(anyLong());
    }

    @Test
    void devePesquisarIgtNoSnitIndicandoOInstrumentoDeQualquerEntidade() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN9");
        Entidade propria = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000719", "Câmara SN9"));
        Entidade outra = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000720", "Câmara SN10"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador SN9", "utilizador-sn9@dgt.pt", "INCM"));
        Instrumento daPropria = new Instrumento(tipoInstrumento, "1", "PDM da própria", criadoPor, criadoPor);
        daPropria.setIdIgtSnit("987109");
        daPropria = instrumentoRepository.saveAndFlush(daPropria);
        Instrumento daOutra = new Instrumento(tipoInstrumento, "1", "PDM da outra", criadoPor, criadoPor);
        daOutra.setIdIgtSnit("987110");
        instrumentoRepository.saveAndFlush(daOutra);
        when(clienteSnit.pesquisar("PDM-SN9", "1111", 1, 20)).thenReturn(new PaginaSnit(List.of(
                new IgtSnit(987109L, "PDM-SN9", "PDM A (SNIT)", "in_force"),
                new IgtSnit(987110L, "PDM-SN9", "PDM B (SNIT)", "in_force"),
                new IgtSnit(987111L, "PDM-SN9", "PDM C (SNIT)", "in_force")), 3, 1, 20));

        mockMvc.perform(get(URL + "/snit").param("tipoInstrumentoId", tipoInstrumento.getId().toString())
                        .param("municipio", "1111").with(jwtComEntidade("501000719")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].idIgtSnit").value("987109"))
                .andExpect(jsonPath("$.content[0].designacao").value("PDM A (SNIT)"))
                .andExpect(jsonPath("$.content[0].tipoInstrumento").value("PDM-SN9"))
                .andExpect(jsonPath("$.content[0].instrumentoId").value(daPropria.getId().toString()))
                .andExpect(jsonPath("$.content[1].instrumentoId").value(daOutra.getId().toString()))
                .andExpect(jsonPath("$.content[2].instrumentoId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void deveDevolver400APesquisarNoSnitSemTipoDeInstrumento() throws Exception {
        mockMvc.perform(get(URL + "/snit").with(jwtComEntidade("501000721")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInstrumentoId"));
        verify(clienteSnit, never()).pesquisar(any(), any(), anyInt(), anyInt());
    }

    @Test
    void deveDevolver500APesquisarNoSnitQuandoOSnitEstaIndisponivel() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN11");
        when(clienteSnit.pesquisar(eq("PDM-SN11"), any(), eq(1), eq(20)))
                .thenThrow(new SnitIndisponivelException("Serviço do SNIT indisponível."));

        mockMvc.perform(get(URL + "/snit").param("tipoInstrumentoId", tipoInstrumento.getId().toString())
                        .with(jwtComEntidade("501000722")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("INTEGRACAO_INDISPONIVEL"));
    }

    @Test
    void deveGravarOsConcelhosDoIgtIntermunicipalDoSnitSemRepetidosELevarOsNaAuditoria() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN40");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000740", "Câmara SN40"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn40@cm-sn40.pt");
        when(clienteSnit.obter(987140L)).thenReturn(Optional.of(new IgtSnit(987140L, "PDM-SN40", "PDM Intermunicipal (SNIT)",
                "in_force", List.of(new MunicipioIgtSnit("1111", "Sintra"), new MunicipioIgtSnit("1105", "Cascais"),
                        new MunicipioIgtSnit("1111", "Sintra")))));

        String corpo = criarInstrumentoDoSnit("501000740", utilizadorId, tipoInstrumento.getId(), "987140")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        UUID instrumentoId = UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
        assertThat(instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId))
                .extracting(InstrumentoConcelho::getMunicipioCodigo).containsExactly("1105", "1111");
        Auditoria registo = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("INSTRUMENTO", instrumentoId.toString())
                .stream().filter(linha -> "CRIACAO".equals(linha.getAcao())).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).get("dtcc").toString()).isEqualTo("[\"1111\",\"1105\"]");
    }

    @Test
    void deveCriarOInstrumentoDoSnitSemConcelhosQuandoOIgtNaoOsTem() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN41");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000741", "Câmara SN41"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-sn41@cm-sn41.pt");
        when(clienteSnit.obter(987141L)).thenReturn(Optional.of(new IgtSnit(987141L, "PDM-SN41", "PDM Nacional (SNIT)", "in_force")));

        String corpo = criarInstrumentoDoSnit("501000741", utilizadorId, tipoInstrumento.getId(), "987141")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        UUID instrumentoId = UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
        assertThat(instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId)).isEmpty();
    }

    @Test
    void deveDevolver401APesquisarNoSnitSemAutenticacao() throws Exception {
        mockMvc.perform(get(URL + "/snit").param("tipoInstrumentoId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveMostrarEmVigorNoPainelDeUmInstrumentoTrazidoDoSnit() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-SN12");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000723", "Câmara SN12"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador SN12", "utilizador-sn12@dgt.pt", "INCM"));
        Instrumento instrumento = new Instrumento(tipoInstrumento, "1", "PDM trazido do SNIT", criadoPor, criadoPor);
        instrumento.setIdIgtSnit("987112");
        instrumento.setEmVigor(true);
        instrumento = instrumentoRepository.saveAndFlush(instrumento);

        mockMvc.perform(get(URL + "/" + instrumento.getId()).with(jwtComEntidade("501000723")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idIgtSnit").value("987112"))
                .andExpect(jsonPath("$.emVigor").value(true));
    }

    @Test
    void deveDevolverInstrumentoNaConsultaAQualquerEntidade() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-200");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000200", "Câmara 200"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 200", "utilizador200@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/200", criadoPor, criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId()).with(jwtComEntidade("501000200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designacao").value("2026/PDM/200"))
                .andExpect(jsonPath("$.numero").value("1"))
                .andExpect(jsonPath("$.entidadeId").doesNotExist());
        mockMvc.perform(get(URL + "/" + instrumento.getId()).with(jwtComEntidade("501000299")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designacao").value("2026/PDM/200"));
    }

    @Test
    void deveDevolver404AoConsultarInstrumentoInexistente() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(jwtComEntidade("501000404")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    private record PainelConfigurado(Instrumento instrumento, Utilizador autor, InstrumentoDinamicaConfig elaboracao,
                                     InstrumentoDinamicaConfig revisao, InstrumentoDinamicaConfig alteracao,
                                     InstrumentoDinamicaConfig suspensao, InstrumentoProcedimentoConfig decisaoInicio,
                                     InstrumentoProcedimentoConfig publicacao, Entidade entidade) {
    }

    /** Quatro tipos de Dinâmica configurados; a Revisão usa o código real porque fecha o ciclo anterior. */
    private PainelConfigurado criarPainel(String codigo, String nif) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        InstrumentoDinamicaConfig elaboracao = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumento, tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"))));
        InstrumentoDinamicaConfig revisao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamicaRevisao()));
        InstrumentoDinamicaConfig alteracao = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumento, tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-" + codigo, "Alteração"))));
        InstrumentoDinamicaConfig suspensao = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumento, tipoDinamicaRepository.saveAndFlush(new TipoDinamica("SUSP-" + codigo, "Suspensão"))));
        InstrumentoProcedimentoConfig decisaoInicio = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                elaboracao, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DEC-" + codigo, "Decisão de início")), 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig publicacao = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                elaboracao, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PUB-" + codigo, "Publicação")), 2, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + codigo, "autor" + codigo + "@cm.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        return new PainelConfigurado(instrumento, autor, elaboracao, revisao, alteracao, suspensao, decisaoInicio, publicacao, entidade);
    }

    private TipoDinamica tipoDinamicaElaboracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
    }

    private TipoDinamica tipoDinamicaRevisao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "REVISAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão")));
    }

    /** `data_inicio` vem de `@CreationTimestamp`: só por SQL se fixa uma ordem cronológica determinista. */
    private Dinamica dinamicaNoPainel(PainelConfigurado painel, InstrumentoDinamicaConfig config, String descritivo,
                                      EstadoDinamica estado, Instant dataInicio, Instant dataConclusao) {
        Dinamica dinamica = new Dinamica(painel.instrumento(), config, descritivo, painel.autor());
        dinamica.setEstado(estado);
        dinamica.setDataConclusao(dataConclusao);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        jdbcTemplate.update("UPDATE dinamica SET data_inicio = ? WHERE id = ?", Timestamp.from(dataInicio), dinamica.getId());
        return dinamica;
    }

    private Processo processoNoPainel(PainelConfigurado painel, Dinamica dinamica, InstrumentoProcedimentoConfig config,
                                      Utilizador autor, int ocorrencia, EstadoProcesso estado) {
        Processo processo = new Processo(dinamica, config, autor, painel.entidade());
        processo.setOcorrencia(ocorrencia);
        processo.setEstado(estado);
        return processoRepository.saveAndFlush(processo);
    }

    @Test
    void deveMostrarNoPainelAsDinamicasPorOrdemCronologicaComSemaforoProcedimentosEProcessos() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL1", "509876101");
        Instant agora = Instant.now();
        Dinamica suspensao = dinamicaNoPainel(painel, painel.suspensao(), "Suspensão parcial", EstadoDinamica.CADUCADA,
                agora.minusSeconds(100), null);
        Dinamica elaboracao = dinamicaNoPainel(painel, painel.elaboracao(), "Elaboração do PDM", EstadoDinamica.TERMINADA,
                agora.minusSeconds(300), agora.minusSeconds(250));
        dinamicaNoPainel(painel, painel.alteracao(), "Alteração da planta", EstadoDinamica.EM_CURSO,
                agora.minusSeconds(200), null);
        Processo decisao = processoNoPainel(painel, elaboracao, painel.decisaoInicio(), painel.autor(), 1, EstadoProcesso.PUBLICADO);
        decisao.setNumeroDr("DR-2026-000001");
        decisao.setDataPublicacaoDr(LocalDate.of(2026, 3, 2));
        decisao.setLinkDr("https://dre.pt/ato/1");
        processoRepository.saveAndFlush(decisao);
        processoNoPainel(painel, elaboracao, painel.publicacao(), painel.autor(), 1, EstadoProcesso.ANULADO);
        processoNoPainel(painel, elaboracao, painel.publicacao(), painel.autor(), 2, EstadoProcesso.PUBLICADO);

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(painel.instrumento().getId().toString()))
                .andExpect(jsonPath("$.designacao").value("2026/PDM/PDM-PAINEL1"))
                .andExpect(jsonPath("$.numero").value("1"))
                .andExpect(jsonPath("$.emVigor").value(false))
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.entidadeId").doesNotExist())
                .andExpect(jsonPath("$.dinamicas", hasSize(4)))
                .andExpect(jsonPath("$.dinamicas[0].id").value(elaboracao.getId().toString()))
                .andExpect(jsonPath("$.dinamicas[0].numeroDinamica").value(matchesPattern("\\d{5}")))
                .andExpect(jsonPath("$.dinamicas[0].descritivo").value("Elaboração do PDM"))
                .andExpect(jsonPath("$.dinamicas[0].tipoDinamica").value("ELAB-PDM-PAINEL1"))
                .andExpect(jsonPath("$.dinamicas[0].nomeDinamica").value("Elaboração"))
                .andExpect(jsonPath("$.dinamicas[0].estado").value("TERMINADA"))
                .andExpect(jsonPath("$.dinamicas[0].tentativa").value(1))
                .andExpect(jsonPath("$.dinamicas[0].semaforo").value("VERDE"))
                .andExpect(jsonPath("$.dinamicas[0].dataConclusao").exists())
                .andExpect(jsonPath("$.dinamicas[0].historica").value(false))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].tipoProcedimento").value("DEC-PDM-PAINEL1"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].nomeProcedimento").value("Decisão de início"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].id").value(decisao.getId().toString()))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].numeroProcesso").value(matchesPattern("\\d{5}")))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].ocorrencia").value(1))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].numeroDr").value("DR-2026-000001"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].dataPublicacaoDr").value("2026-03-02"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos[0].linkDr").value("https://dre.pt/ato/1"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[1].tipoProcedimento").value("PUB-PDM-PAINEL1"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[1].nomeProcedimento").value("Publicação"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[1].processos[0].ocorrencia").value(1))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[1].processos[0].estado").value("ANULADO"))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[1].processos[1].ocorrencia").value(2))
                .andExpect(jsonPath("$.dinamicas[1].descritivo").value("Alteração da planta"))
                .andExpect(jsonPath("$.dinamicas[1].semaforo").value("AMARELO"))
                .andExpect(jsonPath("$.dinamicas[1].procedimentos", hasSize(0)))
                .andExpect(jsonPath("$.dinamicas[2].id").value(suspensao.getId().toString()))
                .andExpect(jsonPath("$.dinamicas[2].estado").value("CADUCADA"))
                .andExpect(jsonPath("$.dinamicas[2].semaforo").value("VERMELHO"))
                .andExpect(jsonPath("$.dinamicas[3].tipoDinamica").value("REVISAO"))
                .andExpect(jsonPath("$.dinamicas[3].nomeDinamica").value("Revisão"))
                .andExpect(jsonPath("$.dinamicas[3].semaforo").value("CINZENTO"))
                .andExpect(jsonPath("$.dinamicas[3].id").doesNotExist())
                .andExpect(jsonPath("$.dinamicas[3].numeroDinamica").doesNotExist())
                .andExpect(jsonPath("$.dinamicas[3].estado").doesNotExist())
                .andExpect(jsonPath("$.dinamicas[3].historica").value(false));
    }

    @Test
    void deveMarcarComoHistoricasAsDinamicasTerminadasAntesDaUltimaRevisaoTerminada() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL2", "509876102");
        painel.instrumento().setEmVigor(true);
        instrumentoRepository.saveAndFlush(painel.instrumento());
        Instant agora = Instant.now();
        dinamicaNoPainel(painel, painel.elaboracao(), "Elaboração do PDM", EstadoDinamica.TERMINADA,
                agora.minusSeconds(500), agora.minusSeconds(400));
        dinamicaNoPainel(painel, painel.revisao(), "1.ª revisão", EstadoDinamica.TERMINADA,
                agora.minusSeconds(300), agora.minusSeconds(200));
        dinamicaNoPainel(painel, painel.alteracao(), "Alteração depois da revisão", EstadoDinamica.TERMINADA,
                agora.minusSeconds(150), agora.minusSeconds(100));
        dinamicaNoPainel(painel, painel.suspensao(), "Suspensão em curso", EstadoDinamica.EM_CURSO,
                agora.minusSeconds(450), null);

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876102")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emVigor").value(true))
                .andExpect(jsonPath("$.dinamicas", hasSize(4)))
                .andExpect(jsonPath("$.dinamicas[0].descritivo").value("Elaboração do PDM"))
                .andExpect(jsonPath("$.dinamicas[0].historica").value(true))
                .andExpect(jsonPath("$.dinamicas[1].descritivo").value("Suspensão em curso"))
                .andExpect(jsonPath("$.dinamicas[1].historica").value(false))
                .andExpect(jsonPath("$.dinamicas[2].descritivo").value("1.ª revisão"))
                .andExpect(jsonPath("$.dinamicas[2].historica").value(false))
                .andExpect(jsonPath("$.dinamicas[3].descritivo").value("Alteração depois da revisão"))
                .andExpect(jsonPath("$.dinamicas[3].historica").value(false));
    }

    @Test
    void deveMostrarOInstrumentoEAsDinamicasAOutraEntidadeMasNaoOsSeusProcessos() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL3", "509876103");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("509876104", "Câmara Outra"));
        Dinamica elaboracao = dinamicaNoPainel(painel, painel.elaboracao(), "Elaboração do PDM", EstadoDinamica.EM_CURSO,
                Instant.now().minusSeconds(60), null);
        processoNoPainel(painel, elaboracao, painel.decisaoInicio(), painel.autor(), 1, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876104")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designacao").value("2026/PDM/PDM-PAINEL3"))
                .andExpect(jsonPath("$.dinamicas[0].id").value(elaboracao.getId().toString()))
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos", hasSize(0)));
        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicas[0].procedimentos[0].processos", hasSize(1)));
    }

    @Test
    void deveListarNoPainelSoAsDinamicasLigadasAoInstrumento() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL5", "509876105");
        Instrumento outro = instrumentoRepository.saveAndFlush(new Instrumento(painel.instrumento().getTipoInstrumento(), "2", "2026/PDM/OUTRO", painel.autor(), painel.autor()));
        dinamicaRepository.saveAndFlush(new Dinamica(outro, painel.elaboracao(), "Elaboração de outro PDM", painel.autor()));

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876105")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicas", hasSize(4)))
                .andExpect(jsonPath("$.dinamicas[*].semaforo", everyItem(is("CINZENTO"))));
    }

    @Test
    void deveCaparTamanhoDaPaginaEm100SemRejeitarOPedido() throws Exception {
        mockMvc.perform(get(URL).param("size", "500").with(jwtComEntidade("501000999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void deveListarTodosOsInstrumentosAQualquerEntidadeComEnvelopeDePaginacao() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDM-300");
        Entidade entidadeA = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000300", "Câmara 300"));
        Entidade entidadeB = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000301", "Câmara 301"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 300", "utilizador300@dgt.pt", "INCM"));
        instrumentoRepository.saveAndFlush(new Instrumento(tipoInstrumento, "1", "2026/PDM/300", criadoPor, criadoPor));
        instrumentoRepository.saveAndFlush(new Instrumento(tipoInstrumento, "2", "2026/PDM/301", criadoPor, criadoPor));

        mockMvc.perform(get(URL).with(jwtComEntidade("501000300")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.designacao=='2026/PDM/300')]").exists())
                .andExpect(jsonPath("$.content[?(@.designacao=='2026/PDM/301')]").exists())
                .andExpect(jsonPath("$.content[0].estado").doesNotExist())
                .andExpect(jsonPath("$.content[0].entidadeId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    void deveFiltrarOsInstrumentosPorTipoConcelhoVigenciaEDesignacaoESemFiltrosResponderComoAntes() throws Exception {
        TipoInstrumento pdm = criarTipoInstrumentoComPermissaoParaEntidade("PDM-313");
        TipoInstrumento outroTipo = criarTipoInstrumentoComPermissaoParaEntidade("PU-313");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000313", "Câmara 313"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 313", "utilizador313@dgt.pt", "INCM"));
        Instrumento sintra = new Instrumento(pdm, "313-1", "PDM de Sintra 313", criadoPor, criadoPor);
        sintra.setEmVigor(true);
        sintra = instrumentoRepository.saveAndFlush(sintra);
        Instrumento cascais = instrumentoRepository.saveAndFlush(new Instrumento(pdm, "313-2", "PDM de Cascais 313", criadoPor, criadoPor));
        Instrumento sintraForaDeVigor = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "313-3", "PDM antigo de Sintra 313", criadoPor, criadoPor));
        Instrumento puDeSintra = new Instrumento(outroTipo, "313-4", "PU de Sintra 313", criadoPor, criadoPor);
        puDeSintra.setEmVigor(true);
        puDeSintra = instrumentoRepository.saveAndFlush(puDeSintra);
        instrumentoConcelhoRepository.saveAllAndFlush(List.of(new InstrumentoConcelho(sintra, "1111"),
                new InstrumentoConcelho(cascais, "1105"), new InstrumentoConcelho(sintraForaDeVigor, "1111"),
                new InstrumentoConcelho(puDeSintra, "1111")));
        RequestPostProcessor sessao = jwtComEntidade("501000313");

        mockMvc.perform(get(URL).with(sessao).param("tipoInstrumentoId", pdm.getId().toString()).param("concelho", "1111")
                        .param("emVigor", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sintra.getId().toString()));
        mockMvc.perform(get(URL).with(sessao).param("tipoInstrumentoId", pdm.getId().toString()).param("designacao", " sintra 313"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(sintra.getId().toString(),
                        sintraForaDeVigor.getId().toString())));
        mockMvc.perform(get(URL).with(sessao).param("designacao", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(URL).with(sessao).param("concelho", "11A1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("concelho"));

        // Sem filtros: todos os Instrumentos, como antes.
        mockMvc.perform(get(URL).with(sessao).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(instrumentoRepository.count()))
                .andExpect(jsonPath("$.content[?(@.designacao=='PDM de Cascais 313')]").exists())
                .andExpect(jsonPath("$.content[?(@.designacao=='PU de Sintra 313')]").exists());
    }

    @Test
    void deveDevolver401ComEnvelopeQuandoSemJwt() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void disponivelNaoDeveExporATentativaEmCursoDeOutraEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-9402");
        Entidade entidadeA = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501009402", "Câmara 9402 A"));
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501009403", "Câmara 9402 B"));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 9402", "utilizador9402@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/9402", autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Elaboração do PDM", autor));
        Processo processo = new Processo(dinamica, cadeia.procedimentoConfig(), autor, entidadeA);
        processo.setOcorrencia(1);
        processo.setEstado(EstadoProcesso.EM_SUBMISSAO);
        processo = processoRepository.saveAndFlush(processo);
        String url = URL + "/" + instrumento.getId() + "/disponivel";
        String procedimento = "$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso";

        mockMvc.perform(get(url).with(jwtComEntidade("501009402")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(procedimento + ".processoId").value(processo.getId().toString()));
        mockMvc.perform(get(url).with(jwtComEntidade("501009403")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(processo.getId().toString()))));
    }

    @Test
    void disponivelDeveDevolverOProcedimentoUnicoDeUmaDinamicaEmCursoSemRegrasConfiguradas() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-501");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000501", "Câmara 501"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 501", "utilizador501@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/501", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000501")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].dinamicaId").value(dinamica.getId().toString()))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].tipoDinamica").value("ELAB-PDM-501"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].nomeDinamica").value("Elaboração"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].instrumentoProcedimentoConfigId")
                        .value(cadeia.procedimentoConfig().getId()))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tipoProcedimento").value("PROC-PDM-501"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].nomeProcedimento").value("Procedimento PDM-501"));
    }

    @Test
    void disponivelSoDeveOferecerOProcedimentoDaParteCAQuemOPodePublicar() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-4490", "SEGUNDA_C");
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, cadeia.procedimentoConfig()));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501044900", "Câmara 4490"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 4490", "utilizador4490@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/4490", criadoPor, criadoPor));
        dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Elaboração do PDM", criadoPor));
        String url = URL + "/" + instrumento.getId() + "/disponivel";
        Map<String, Object> atos = Map.of("serie_1", List.of(), "serie_2", List.of(Map.of("id", 1L, "nome", "Ato 1", "extrato", false)));

        mockMvc.perform(get(url).with(jwtComParteDr("501044900", "H", atos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(0)));

        mockMvc.perform(get(url).with(jwtComParteDr("501044900", "C", atos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(1)));

        mockMvc.perform(get(url).with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(0)));

        mockMvc.perform(get(url).with(jwtComTipoEntidade("501044900", UUID.randomUUID(), "DGT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].instrumentoProcedimentoConfigId")
                        .value(cadeia.procedimentoConfig().getId()));
    }

    private RequestPostProcessor jwtComParteDr(String nif, String letra, Map<String, Object> atosAutorizados) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", comId(nif, Map.of("tipo_entidade", "CM", "nif", nif, "nome", "Entidade Teste", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", letra, "descricao", "Parte " + letra),
                        "atos_autorizados", atosAutorizados))));
    }

    @Test
    void disponivelDeveDevolverOCodigoEONomeDasDinamicasDisponiveis() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-3670");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501036700", "Câmara 3670"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 3670", "utilizador3670@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/3670", criadoPor, criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501036700")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[0].tipoDinamica").value("ELAB-PDM-3670"))
                .andExpect(jsonPath("$.dinamicasDisponiveis[0].nomeDinamica").value("Elaboração"));
    }

    @Test
    void disponivelNaoDeveDevolverOFormularioSchemaDoProcedimento() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-588");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000588", "Câmara 588"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 588", "utilizador588@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/588", criadoPor, criadoPor));
        dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000588")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0]", not(hasKey("formularioSchema"))));
    }

    @Test
    void disponivelDeveDevolverDataCaducidadeNulaNaDinamicaEmCurso() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-950");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000950", "Câmara 950"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 950", "utilizador950@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/950", criadoPor, criadoPor));
        dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000950")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].dataCaducidade").isEmpty());
    }

    @Test
    void disponivelDeveDevolverDataCaducidadeQuandoPreenchida() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-951");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000951", "Câmara 951"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 951", "utilizador951@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/951", criadoPor, criadoPor));
        Dinamica dinamica = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        dinamica.setDataCaducidade(java.time.LocalDate.of(2027, 1, 31));
        dinamicaRepository.saveAndFlush(dinamica);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000951")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].dataCaducidade").value("2027-01-31"));
    }

    @Test
    void disponivelDeveDevolverOInstrumentoDeOutraEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-502");
        Entidade entidadeDona = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000502", "Câmara Dona 502"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 502", "utilizador502@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/502", criadoPor, criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000503")))
                .andExpect(status().isOk());
    }

    @Test
    void disponivelDeveDevolverUltimaDinamicaPublicadaNulaQuandoNenhumaConcluida() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-503");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000504", "Câmara 504"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 504", "utilizador504@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/504", criadoPor, criadoPor));
        Dinamica emCurso = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        dinamicaRepository.saveAndFlush(emCurso);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000504")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaDinamicaPublicada").doesNotExist());
    }

    @Test
    void disponivelDeveDevolverUltimaDinamicaPublicadaQuandoExisteUmaConcluida() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-505");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000505", "Câmara 505"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 505", "utilizador505@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/505", criadoPor, criadoPor));
        Dinamica concluida = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        concluida.setEstado(EstadoDinamica.TERMINADA);
        concluida.setDataConclusao(Instant.parse("2026-08-15T10:00:00Z"));
        dinamicaRepository.saveAndFlush(concluida);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000505")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dinamicaId").value(concluida.getId().toString()))
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.tipoDinamica").value(cadeia.dinamicaConfig().getTipoDinamica().getCodigo()))
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.nomeDinamica").value("Elaboração"))
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dataConclusao").value("2026-08-15T10:00:00Z"));
    }

    @Test
    void disponivelDeveDevolverAMaisRecenteQuandoVariasDinamicasConcluidas() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-506");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000506", "Câmara 506"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 506", "utilizador506@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/506", criadoPor, criadoPor));
        Dinamica antiga = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        antiga.setEstado(EstadoDinamica.TERMINADA);
        antiga.setDataConclusao(Instant.parse("2025-01-10T10:00:00Z"));
        dinamicaRepository.saveAndFlush(antiga);
        Dinamica maisRecente = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        maisRecente.setEstado(EstadoDinamica.TERMINADA);
        maisRecente.setDataConclusao(Instant.parse("2026-08-15T10:00:00Z"));
        dinamicaRepository.saveAndFlush(maisRecente);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000506")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaDinamicaPublicada.dinamicaId").value(maisRecente.getId().toString()));
    }

    @Test
    void disponivelDeveDevolverTentativaEmCursoNulaQuandoNaoHaTentativaNenhuma() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-510");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000510", "Câmara 510"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 510", "utilizador510@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/510", criadoPor, criadoPor));
        dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000510")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso").doesNotExist());
    }

    @Test
    void disponivelDeveMarcarTentativaEmCursoQuandoUltimaTentativaEstaEmSubmissao() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-511");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000511", "Câmara 511"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 511", "utilizador511@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/511", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        Processo rascunho = new Processo(dinamica, cadeia.procedimentoConfig(), criadoPor, entidade);
        processoRepository.saveAndFlush(rascunho);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000511")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso.processoId")
                        .value(rascunho.getId().toString()))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso.numeroTentativa").value(1))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso.estado").value("EM_SUBMISSAO"));
    }

    @Test
    void disponivelNaoDeveIncluirProcedimentoComUltimaTentativaSubmetida() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-512");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000512", "Câmara 512"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 512", "utilizador512@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/512", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        Processo submetido = new Processo(dinamica, cadeia.procedimentoConfig(), criadoPor, entidade);
        submetido.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(submetido);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000512")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(0)));
    }

    @Test
    void disponivelDeveDevolverTentativaEmCursoNulaQuandoUltimaTentativaFoiAnulada() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-513");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000513", "Câmara 513"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 513", "utilizador513@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/513", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        Processo anulado = new Processo(dinamica, cadeia.procedimentoConfig(), criadoPor, entidade);
        anulado.setEstado(EstadoProcesso.ANULADO);
        processoRepository.saveAndFlush(anulado);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501000513")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tentativaEmCurso").doesNotExist());
    }

    /** Sem Regra configurada, a Dinâmica é elegível. */
    @Test
    void deveDevolver201AoIniciarDinamicaElegivel() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-601");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000601", "Câmara 601"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 601", "instrumento601@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador601@cm-601.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/601", criadoPor, criadoPor));
        CriarDinamicaRequest pedido = new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração do PDM");

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComEntidadeEUtilizadorReal("501000601", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_CURSO"))
                .andExpect(jsonPath("$.numeroDinamica").value(matchesPattern("\\d{5}")))
                .andExpect(jsonPath("$.descritivo").value("Alteração do PDM"))
                .andExpect(jsonPath("$.tentativa").value(1))
                .andExpect(jsonPath("$.origemDados").value("NATIVO"))
                .andExpect(jsonPath("$.instrumentoDinamicaConfigId").value(cadeia.dinamicaConfig().getId()))
                .andExpect(jsonPath("$.nomeDinamica").value("Elaboração"))
                .andExpect(jsonPath("$.dataInicio").exists());
    }

    @Test
    void deveDevolver400AoIniciarDinamicaSemDescritivoOuComMaisDeCinquentaCaracteres() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-602");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000602", "Câmara 602"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 602", "instrumento602@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador602@cm-602.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/602", criadoPor, criadoPor));

        for (String descritivo : new String[] {null, " ", "x".repeat(51)}) {
            mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas")
                            .with(jwtComEntidadeEUtilizadorReal("501000602", utilizadorId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(
                                    new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), descritivo))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        }
        assertThat(dinamicaRepository.findByInstrumento_IdAndEstado(instrumento.getId(), EstadoDinamica.EM_CURSO)).isEmpty();
    }

    @Test
    void deveDevolver403AoIniciarDinamicaSemPermissaoAcao() throws Exception {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM-637", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-637", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        // Sem InstrumentoProcedimentoConfig/PermissaoAcao — nenhum ator tem permissão nesta Dinâmica.
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000637", "Câmara 637"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 637", "instrumento637@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador637@cm-637.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/637", criadoPor, criadoPor));
        CriarDinamicaRequest pedido = new CriarDinamicaRequest(dinamicaConfig.getId(), "Alteração do PDM");

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComEntidadeEUtilizadorReal("501000637", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver409QuandoDinamicaNaoElegivelPeloMotorDeRegras() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-609");
        Regra regra = Regra.paraDinamica(cadeia.dinamicaConfig());
        regraRepository.saveAndFlush(regra);
        condicaoRepository.saveAndFlush(new Condicao(regra, "OCORRENCIA_EM_ESTADO",
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"NUNCA-EXISTE\",\"estado\":\"TERMINADA\"}"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000609", "Câmara 609"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 609", "instrumento609@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador609@cm-609.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/609", criadoPor, criadoPor));
        CriarDinamicaRequest pedido = new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração do PDM");

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComEntidadeEUtilizadorReal("501000609", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_NAO_ELEGIVEL"));

        assertThat(dinamicaRepository.findByInstrumento_IdAndEstado(instrumento.getId(), EstadoDinamica.EM_CURSO)).isEmpty();
    }

    @Test
    void deveIniciarDinamicaNumInstrumentoQueNaoTemEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-604");
        Entidade entidadeDona = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000604", "Câmara Dona 604"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 604", "utilizador604@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/604", criadoPor, criadoPor));
        CriarDinamicaRequest pedido = new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração do PDM");

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComEntidadeEUtilizadorReal("501000605", criadoPor.getId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated());
    }

    /** Sem `atos_autorizados` no token, a verificação do tipo de ato não se aplica. */
    @Test
    void disponivelDeveListarAsDinamicasEmCursoECaducadasMasNaoAsTerminadas() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-6301");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501006301", "Câmara 6301"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador 6301", "utilizador6301@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/6301", criadoPor, criadoPor));
        Dinamica emCurso = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Em curso", criadoPor));
        Dinamica caducada = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Caducada", criadoPor);
        caducada.setEstado(EstadoDinamica.CADUCADA);
        caducada = dinamicaRepository.saveAndFlush(caducada);
        Dinamica terminada = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Terminada", criadoPor);
        terminada.setEstado(EstadoDinamica.TERMINADA);
        terminada.setDataConclusao(Instant.parse("2026-08-15T10:00:00Z"));
        terminada = dinamicaRepository.saveAndFlush(terminada);

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtComEntidade("501006301")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso", hasSize(2)))
                .andExpect(jsonPath("$.dinamicasEmCurso[*].dinamicaId")
                        .value(containsInAnyOrder(emCurso.getId().toString(), caducada.getId().toString())))
                .andExpect(jsonPath("$.dinamicasEmCurso[*].dinamicaId").value(not(hasItem(terminada.getId().toString()))))
                .andExpect(jsonPath("$.dinamicasEmCurso[?(@.dinamicaId == '" + caducada.getId() + "')].estado")
                        .value(contains("CADUCADA")))
                .andExpect(jsonPath("$.dinamicasEmCurso[?(@.dinamicaId == '" + emCurso.getId() + "')].estado")
                        .value(contains("EM_CURSO")))
                .andExpect(jsonPath("$.dinamicasEmCurso[*].procedimentosDisponiveis", hasSize(2)));
    }

    @Test
    void deveDevolver201AoCriarProcessoNumaDinamicaCaducada() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-6302");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501006302", "Câmara 6302"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 6302", "instrumento6302@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador6302@cm-6302.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/6302", criadoPor, criadoPor));
        Dinamica dinamica = new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor);
        dinamica.setEstado(EstadoDinamica.CADUCADA);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        TipoAto tipoAto = new TipoAto("ATO-6302", "Ato 6302", 1);
        tipoAto.setIncmActTypeIdSerie2(6302L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        CriarProcessoRequest pedido = new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId());

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos")
                        .with(jwtComEntidadeEUtilizadorReal("501006302", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));
    }

    @Test
    void deveDevolver201AoCriarProcessoElegivel() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-701");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000701", "Câmara 701"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 701", "instrumento701@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador701@cm-701.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/701", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-701", "Ato 701", 1);
        tipoAto.setIncmActTypeIdSerie2(701L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        CriarProcessoRequest pedido = new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId());

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComEntidadeEUtilizadorReal("501000701", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroProcesso").value(matchesPattern("\\d{5}")))
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.ocorrencia").value(1))
                .andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.origemDados").value("NATIVO"))
                .andExpect(jsonPath("$.tipoAtoId").value(tipoAto.getId()));
    }

    @Test
    void deveDevolver403QuandoTipoAtoNaoConstaDosAtosAutorizados() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-702");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000702", "Câmara 702"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 702", "instrumento702@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador702@cm-702.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/702", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-702", "Ato 702", 1);
        tipoAto.setIncmActTypeIdSerie2(702L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        CriarProcessoRequest pedido = new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId());
        Map<String, Object> atosAutorizadosSemEsteAto = Map.of("serie_1", List.of(), "serie_2", List.of());

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos")
                        .with(jwtComAtosAutorizados("501000702", utilizadorId, atosAutorizadosSemEsteAto))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TIPO_ATO_NAO_AUTORIZADO"));
    }

    @Test
    void deveDevolver409AoCriarProcessoBloqueadoPorSequenciamento() throws Exception {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM-703", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-703", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO-703", "Decisão")), 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DISCUSSAO-703", "Discussão")), 2, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, ordem2));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000703", "Câmara 703"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 703", "instrumento703@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador703@cm-703.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/703", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-703", "Ato 703", 1);
        tipoAto.setIncmActTypeIdSerie2(703L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        // ordem1 nunca foi publicado — ordem2 fica bloqueado pelo sequenciamento por omissão.
        CriarProcessoRequest pedido = new CriarProcessoRequest(ordem2.getId(), tipoAto.getId());

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComEntidadeEUtilizadorReal("501000703", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEQUENCIAMENTO_NAO_CUMPRIDO"));
    }

    @Test
    void deveDevolver409QuandoJaExisteTentativaEmSubmissao() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-704");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000704", "Câmara 704"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento 704", "instrumento704@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador704@cm-704.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/704", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-704", "Ato 704", 1);
        tipoAto.setIncmActTypeIdSerie2(704L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        CriarProcessoRequest pedido = new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId());
        RequestPostProcessor jwtUtilizador = jwtComEntidadeEUtilizadorReal("501000704", utilizadorId);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtUtilizador)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtUtilizador)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));
    }

    private record TentativaExistente(Instrumento instrumento, Dinamica dinamica, Processo processo, UUID autorId) {
    }

    private void comItemRejeitadoNoRascunho(TentativaExistente tentativa) {
        ProcessoInformacaoBase elemento = new ProcessoInformacaoBase(tentativa.processo(), ElementoInformacaoBase.DELIBERACAO);
        elemento.setEstadoValidacao(EstadoValidacao.REJEITADO);
        ProcessoValidacaoRejeicao linha = ProcessoValidacaoRejeicao.daInformacaoBase(
                processoInformacaoBaseRepository.saveAndFlush(elemento));
        linha.registar(null, null, "Falta a deliberação.",
                utilizadorRepository.findById(tentativa.autorId()).orElseThrow(), Instant.now());
        processoValidacaoRejeicaoRepository.saveAndFlush(linha);
    }

    private TentativaExistente criarTentativaExistente(String codigo, String nif) {
        return criarTentativaExistente(codigo, nif, "SEGUNDA_H");
    }
    
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Itens não obrigatórios: só marcam o elemento como previsto, sem criar pendências documentais. */
    private void preverInformacaoBase(InstrumentoProcedimentoConfig procedimentoConfig, ElementoInformacaoBase... elementos) {
        int ordem = 1;
        for (ElementoInformacaoBase elemento : elementos) {
            DocumentoConfig item = new DocumentoConfig(procedimentoConfig, elemento.name() + "_PUB", elemento.name(),
                    elemento.name(), CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                    "[\"pdf\"]", ordem++);
            item.setElementoInformacaoBase(elemento);
            documentoConfigRepository.saveAndFlush(item);
        }
    }

    private TentativaExistente criarTentativaExistente(String codigo, String nif, String serieDr) {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade(codigo, serieDr, tipoProcedimentoComFormulario());
        preverInformacaoBase(cadeia.procedimentoConfig(), ElementoInformacaoBase.values());
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        UUID autorId = criarUtilizadorIncm("autor" + codigo + "@cm-" + codigo + ".pt");
        Utilizador autor = utilizadorRepository.findById(autorId).orElseThrow();
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", autor));
        Processo novo = new Processo(dinamica, cadeia.procedimentoConfig(), autor, entidade);
        novo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        novo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        novo.setEntidadeEmitente("Câmara Municipal de Teste");
        Processo processo = processoRepository.saveAndFlush(novo);
        return new TentativaExistente(instrumento, dinamica, processo, autorId);
    }

    /** Lida da base no momento do pedido; inexistente → 0, para os testes que nunca chegam a carregar o processo. */
    private long versao(Object processoId) {
        return processoRepository.findById(UUID.fromString(processoId.toString())).map(Processo::getVersao).orElse(0L);
    }

    private String corpoVersao(Object processoId) {
        return "{\"versao\":" + versao(processoId) + "}";
    }

    /** O envio à INCM é do `EnvioIncmService` (testado à parte): aqui só se vê o que o endpoint faz com o resultado. */
    private void envioLevaPara(UUID processoId, EstadoProcesso estadoFinal) {
        when(envioIncmService.enviar(eq(processoId), any())).thenAnswer(invocacao -> {
            Processo processo = processoRepository.findById(processoId).orElseThrow();
            processo.setEstado(estadoFinal);
            return processo;
        });
    }

    @Test
    void deveDevolverAVersaoSeguinteAoGravarRascunhoComAVersaoLida() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V01", "501009001");
        UUID id = tentativa.processo().getId();
        long lida = versao(id);

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501009001", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + lida + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(lida + 1));

        assertThat(versao(id)).isEqualTo(lida + 1);
    }

    @Test
    void deveDevolver409VersaoDesatualizadaESemAlterarNadaQuandoARascunhoChegaComVersaoAntiga() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V02", "501009002");
        UUID id = tentativa.processo().getId();
        long antiga = versao(id);
        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501009002", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + antiga + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"primeiro\"}}}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501009002", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + antiga + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"segundo\"}}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"))
                .andExpect(jsonPath("$.mensagem").value("O processo foi alterado; recarregue."));

        Processo guardado = processoRepository.findById(id).orElseThrow();
        assertThat(guardado.getVersao()).isEqualTo(antiga + 1);
        assertThat(guardado.getDadosFormulario()).contains("primeiro").doesNotContain("segundo");
    }

    @Test
    void deveAceitarSoUmDosDoisPedidosQuandoAutorResgataEDgtCapturaComAMesmaVersao() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V03", "501009003");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();
        String mesmaVersao = corpoVersao(id);

        mockMvc.perform(patch("/api/v1/processos/" + id + "/resgatar")
                        .with(jwtComEntidadeEUtilizadorReal("501009003", tentativa.autorId()))
                        .contentType("application/json")
                        .content(mesmaVersao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(mesmaVersao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveDevolverVersaoDesatualizadaEmVezDeEstadoInvalidoQuandoOAutorResgataDepoisDaCaptura() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V04", "501009004");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();
        String mesmaVersao = corpoVersao(id);

        mockMvc.perform(patch("/api/v1/processos/" + id + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(mesmaVersao))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/processos/" + id + "/resgatar")
                        .with(jwtComEntidadeEUtilizadorReal("501009004", tentativa.autorId()))
                        .contentType("application/json")
                        .content(mesmaVersao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
    }

    @Test
    void deveCorrigirRejeitadoDgtEManterIdNumeroEOcorrencia() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C01", "501009601");
        tentativa.processo().setEstado(EstadoProcesso.REJEITADO_DGT);
        Processo antes = processoRepository.saveAndFlush(tentativa.processo());
        UUID id = antes.getId();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                        .with(jwtComEntidadeEUtilizadorReal("501009601", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numeroProcesso").value(antes.getNumeroProcesso()))
                .andExpect(jsonPath("$.ocorrencia").value(antes.getOcorrencia()))
                .andExpect(jsonPath("$.versao").value(antes.getVersao() + 1));
    }

    @Test
    void deveCorrigirAnuladoIncmEPermitirEditarESubmeterDepois() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C02", "501009102");
        tentativa.processo().setEstado(EstadoProcesso.ANULADO_INCM);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();
        RequestPostProcessor autor = jwtComEntidadeEUtilizadorReal("501009102", tentativa.autorId());

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario").with(autor)
                        .contentType("application/json").content("{\"versao\":" + versao(id) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir").with(autor)
                        .contentType("application/json").content(corpoVersao(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario").with(autor)
                        .contentType("application/json").content("{\"versao\":" + versao(id) + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void deveDevolver409EstadoInvalidoAoCorrigirForaDeRejeitadoDgtEAnuladoIncm() throws Exception {
        List<EstadoProcesso> estados = List.of(EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.VALIDADO_DGT,
                EstadoProcesso.PUBLICADO, EstadoProcesso.ANULADO);
        int indice = 0;
        for (EstadoProcesso estado : estados) {
            String nif = "50100920" + indice;
            TentativaExistente tentativa = criarTentativaExistente("PDM-C03-" + indice, nif);
            tentativa.processo().setEstado(estado);
            processoRepository.saveAndFlush(tentativa.processo());
            UUID id = tentativa.processo().getId();

            mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                            .with(jwtComEntidadeEUtilizadorReal(nif, tentativa.autorId()))
                            .contentType("application/json")
                            .content(corpoVersao(id)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                    .andExpect(jsonPath("$.mensagem").value("Este processo não pode ser corrigido neste estado."));

            assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(estado);
            indice++;
        }
    }

    @Test
    void deveDevolverVersaoDesatualizadaAoCorrigirMesmoComEstadoInvalido() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C04", "501009104");
        tentativa.processo().setEstado(EstadoProcesso.PUBLICADO);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                        .with(jwtComEntidadeEUtilizadorReal("501009104", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + (versao(id) + 7) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
    }

    @Test
    void deveRecusarCorrigirAQuemNaoEOAutorENegarAOutraEntidadeESemToken() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C05", "501009105");
        tentativa.processo().setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();
        UUID outroDaEntidade = criarUtilizadorIncm("outro-c05@cm-c05.pt");
        UUID deOutraEntidade = criarUtilizadorIncm("terceiro-c05@cm-c05b.pt");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501009199", "Câmara C05b"));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                        .with(jwtComEntidadeEUtilizadorReal("501009105", outroDaEntidade))
                        .contentType("application/json").content(corpoVersao(id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                        .with(jwtComEntidadeEUtilizadorReal("501009199", deOutraEntidade))
                        .contentType("application/json").content(corpoVersao(id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(patch("/api/v1/processos/" + id + "/corrigir")
                        .contentType("application/json").content(corpoVersao(id)))
                .andExpect(status().isUnauthorized());

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
    }

    @Test
    void deveDevolver400ValidacaoAoCorrigirSemVersao() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C06", "501009106");
        tentativa.processo().setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/corrigir")
                        .with(jwtComEntidadeEUtilizadorReal("501009106", tentativa.autorId()))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveListarCorrigirNasAcoesDisponiveisDoAutorEmAnuladoIncm() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-C07", "501009107");
        tentativa.processo().setEstado(EstadoProcesso.ANULADO_INCM);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID outroDaEntidade = criarUtilizadorIncm("outro-c07@cm-c07.pt");
        String url = "/api/v1/processos/" + tentativa.processo().getId();

        mockMvc.perform(get(url).with(jwtComEntidadeEUtilizadorReal("501009107", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis", contains("corrigir")));
        mockMvc.perform(get(url).with(jwtComEntidadeEUtilizadorReal("501009107", outroDaEntidade)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("corrigir"))));
    }

    @Test
    void deveDevolver409QuandoSeCriaProcessoDepoisDeAnuladoIncm() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-C08");
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501009108", "Câmara C08"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento C08", "instrumento-c08@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-c08@cm-c08.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/C08", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        Processo anulado = new Processo(dinamica, cadeia.procedimentoConfig(), criadoPor, entidade);
        anulado.setEstado(EstadoProcesso.ANULADO_INCM);
        processoRepository.saveAndFlush(anulado);
        TipoAto tipoAto = new TipoAto("ATO-C08", "Ato C08", 1);
        tipoAto.setIncmActTypeIdSerie2(808L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        RequestPostProcessor jwtUtilizador = jwtComEntidadeEUtilizadorReal("501009108", utilizadorId);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtUtilizador)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_EM_CURSO"));

        mockMvc.perform(get(URL + "/" + instrumento.getId() + "/disponivel").with(jwtUtilizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis", hasSize(0)));
    }

    @Test
    void deveDevolver400ValidacaoQuandoAEscritaChegaSemVersao() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V05", "501009005");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("versao"));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501009005", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("versao"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveDevolverAVersaoNoDetalheDoProcesso() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V06", "501009006");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501009006", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao(id)));
    }

    @Test
    void naoDeveDevolverOFormularioSchemaNoDetalheDoProcesso() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-V88", "501009088");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501009088", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey("formularioSchema"))))
                .andExpect(jsonPath("$", not(hasKey("formularioSchemaId"))));
    }

    @Test
    void deveDevolverNoDetalheOsDadosDaPublicacaoDeUmProcessoPublicado() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-U95A", "501009951");
        UUID id = tentativa.processo().getId();
        Processo processo = processoRepository.findById(id).orElseThrow();
        processo.setEstado(EstadoProcesso.PUBLICADO);
        processo.setNumeroDr("DR-2026-000001");
        processo.setDataPublicacaoDr(LocalDate.of(2026, 10, 24));
        processo.setLinkDr("https://exemplo.pt/dr/2026/000001");
        processo.setNumeroDeposito("03.11.11/PDM/02/2026/1");
        processoRepository.saveAndFlush(processo);

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501009951", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.numeroDr").value("DR-2026-000001"))
                .andExpect(jsonPath("$.dataPublicacaoDr").value("2026-10-24"))
                .andExpect(jsonPath("$.linkDr").value("https://exemplo.pt/dr/2026/000001"))
                .andExpect(jsonPath("$.numeroDeposito").value("03.11.11/PDM/02/2026/1"));
    }

    @Test
    void deveDevolverNulosNoDetalheOsDadosDaPublicacaoDeUmProcessoNaoPublicado() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-U95B", "501009952");

        mockMvc.perform(get("/api/v1/processos/" + tentativa.processo().getId())
                        .with(jwtComEntidadeEUtilizadorReal("501009952", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dataPublicacaoDr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.linkDr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.numeroDeposito").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveDevolver200AoGravarRascunhoValido() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-801", "501000801");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000801", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.dadosFormulario").value(org.hamcrest.Matchers.containsString("textoPublicar")));
    }

    @Test
    void deveDevolver403AoGravarRascunhoQuandoNaoEAutor() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-802", "501000802");
        UUID outroUtilizador = criarUtilizadorIncm("outro802@cm-802.pt");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000802", outroUtilizador))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveGravarCamposIncmNoRascunhoEDevolveLaNoDetalhe() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-816", "501000816");
        String id = tentativa.processo().getId().toString();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000816", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"sumario\":\"Sumário em rascunho\",\"dataAssinatura\":\"2026-09-01\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.ocorrencia").value(tentativa.processo().getOcorrencia()));

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501000816", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sumario").value("Sumário em rascunho"))
                .andExpect(jsonPath("$.dataAssinatura").value("2026-09-01"))
                .andExpect(jsonPath("$.tipoInformacao").value("TEXTO_INTEGRAL"))
                .andExpect(jsonPath("$.normaLegal").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveDevolver403AoGravarCamposIncmQuandoNaoEAutor() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-817", "501000817");
        UUID outroUtilizador = criarUtilizadorIncm("outro817@cm-817.pt");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000817", outroUtilizador))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @ParameterizedTest
    @CsvSource({"TECNICO, 818", "MASTER, 820"})
    void deveDevolver403AoGravarCamposIncmQuandoInternoSemPermissaoAcao(String perfilInterno, String codigo) throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-" + codigo, "501000" + codigo);

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), perfilInterno))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver401AoGravarCamposIncmSemJwt() throws Exception {
        mockMvc.perform(put("/api/v1/processos/" + UUID.randomUUID() + "/dados-formulario")
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(UUID.randomUUID()) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void deveDevolver404AoGravarCamposIncmDeOutraEntidade() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-821", "501000821");
        UUID outroUtilizador = criarUtilizadorIncm("outro821@cm-822.pt");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000822", outroUtilizador))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver409AoGravarCamposIncmQuandoJaEnviadoDgt() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-823", "501000823");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000823", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    @Test
    void deveDevolver400AoGravarCamposIncmComTipoInformacaoForaDoEnum() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-824", "501000824");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000824", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"tipoInformacao\":\"PARCIAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
    }

    @Test
    void deveDevolver400AoGravarCamposIncmComDataMalFormada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-826", "501000826");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000826", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"dataAssinatura\":\"2026-13-45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("dataAssinatura"));
    }

    @Test
    void deveDevolver400AoGravarCamposIncmComJsonMalFormado() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-827", "501000827");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000827", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400AoGravarCamposIncmComSumarioAcimaDoLimite() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-825", "501000825");

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000825", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"" + "s".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("sumario"));
    }

    @Test
    void deveGravarOsSeisCamposIncmNumSoPedidoEDevolverAVersaoSeguinte() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-835", "501000835");
        UUID id = tentativa.processo().getId();
        long lida = versao(id);

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000835", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + lida + ",\"sumario\":\"Sumário\",\"dataAssinatura\":\"2026-09-01\","
                                + "\"normaLegal\":\"Norma X\",\"tipoInformacao\":\"EXTRATO\",\"publicarSuplemento\":true,"
                                + "\"dataSuplemento\":\"2026-09-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(lida + 1));

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501000835", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sumario").value("Sumário"))
                .andExpect(jsonPath("$.dataAssinatura").value("2026-09-01"))
                .andExpect(jsonPath("$.normaLegal").value("Norma X"))
                .andExpect(jsonPath("$.tipoInformacao").value("EXTRATO"))
                .andExpect(jsonPath("$.publicarSuplemento").value(true))
                .andExpect(jsonPath("$.dataSuplemento").value("2026-09-02"));
    }

    @Test
    void deveGravarONuloNosCamposOmitidosESemValidarObrigatoriedade() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-836", "501000836");
        UUID id = tentativa.processo().getId();
        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000836", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"sumario\":\"Sumário\",\"normaLegal\":\"Norma X\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000836", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501000836", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sumario").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.normaLegal").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveGravarAInformacaoBaseEmDadosFormularioEDevolveLaNoDetalhe() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-832", "501000832");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000832", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":" + INFORMACAO_BASE_COMPLETA + "}"))
                .andExpect(status().isOk());

        String detalhe = mockMvc.perform(get("/api/v1/processos/" + id)
                        .with(jwtComEntidadeEUtilizadorReal("501000832", tentativa.autorId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode dados = objectMapper.readTree(objectMapper.readTree(detalhe).get("dadosFormulario").asText());
        assertThat(dados.get("informacaoBase")).isEqualTo(objectMapper.readTree(INFORMACAO_BASE_COMPLETA));
    }

    @Test
    void deveGravarADataDeAssinaturaNaColunaESemARepetirEmDadosFormulario() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-860", "501000860");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000860", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"dataAssinatura\":\"2026-09-02\",\"informacaoBase\":"
                                + INFORMACAO_BASE_COMPLETA + "}"))
                .andExpect(status().isOk());

        Processo guardado = processoRepository.findById(id).orElseThrow();
        assertThat(guardado.getDataAssinatura()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(guardado.getDadosFormulario()).doesNotContain("2026-09-02").doesNotContain("dataAssinatura");
    }

    @Test
    void deveDeixarDadosFormularioNuloQuandoOPedidoNaoTemInformacaoBase() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-861", "501000861");
        UUID id = tentativa.processo().getId();
        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000861", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":" + INFORMACAO_BASE_COMPLETA + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000861", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "}"))
                .andExpect(status().isOk());

        assertThat(processoRepository.findById(id).orElseThrow().getDadosFormulario()).isNull();
    }

    @Test
    void deveDevolver400ComOCaminhoDoCampoQuandoOPrazoDaDeliberacaoENegativo() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-862", "501000862");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000862", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":{\"deliberacao\":{\"prazoMeses\":-1}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao.prazoMeses"))
                .andExpect(jsonPath("$.campos[0].erro").value("O prazo tem de ser um número inteiro não negativo."));

        assertThat(processoRepository.findById(id).orElseThrow().getDadosFormulario()).isNull();
    }

    @Test
    void deveDevolver400ComOCaminhoDoCampoQuandoOPrazoDoTextoDoAtoENegativo() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-863", "501000863");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000863", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":-1}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.textoAto.prazoAnos"));
    }

    @Test
    void deveDevolver400ComOCaminhoDoCampoQuandoOPrazoTemDecimais() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-9101", "501009101");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501009101", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":{\"deliberacao\":{\"prazoMeses\":1.5}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao.prazoMeses"));
    }

    @Test
    void deveDevolver400ComOCaminhoDoCampoQuandoADataDaDeliberacaoEInvalida() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-864", "501000864");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000864", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":{\"deliberacao\":{\"dataDeliberacao\":\"2026-13-45\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao.dataDeliberacao"));
    }

    @Test
    void deveDevolver400ComOCaminhoDoCampoQuandoOTextoAPublicarExcede2000Caracteres() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-865", "501000865");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000865", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\""
                                + "x".repeat(2001) + "\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao.textoPublicar"))
                .andExpect(jsonPath("$.campos[0].erro").value("O texto a publicar excede 2000 caracteres."));
    }

    @Test
    void deveGravarComPrazosEDatasDiferentesEntreAsDeliberacaoEOTextoDoAtoSemValidacaoCruzada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-866", "501000866");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000866", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"dataAssinatura\":\"2026-01-01\",\"informacaoBase\":{"
                                + "\"deliberacao\":{\"prazoAnos\":1,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2026-12-31\"},"
                                + "\"textoAto\":{\"prazoAnos\":9,\"avaliacaoAmbiental\":false}}}"))
                .andExpect(status().isOk());
    }

    @Test
    void deveDevolver404NaRotaPublicacaoIncmRetirada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-833", "501000833");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/publicacao-incm")
                        .with(jwtComEntidadeEUtilizadorReal("501000833", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + ",\"sumario\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveDevolver409VersaoDesatualizadaAoGravarCamposIncmComVersaoAntiga() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-834", "501000834");
        UUID id = tentativa.processo().getId();
        long antiga = versao(id);
        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000834", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + antiga + ",\"sumario\":\"primeiro\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501000834", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + antiga + ",\"sumario\":\"segundo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        assertThat(processoRepository.findById(id).orElseThrow().getSumario()).isEqualTo("primeiro");
    }

    @Test
    void deveDevolver403AoResgatarQuandoTecnico() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-819", "501000819");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/resgatar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver200AoSubmeterComSucesso() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-803", "501000803");
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(versao(tentativa.processo().getId()), "Resumo do ato.", java.time.LocalDate.now(),
                "Norma Legal X", "TEXTO_INTEGRAL", null, null);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501000803", tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"))
                .andExpect(jsonPath("$.dataSubmissao").exists());
    }

    @Test
    void deveIgnorarDataLimiteNoPedidoDeSubmissaoENaoADevolverNoProcesso() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-952", "501000952");
        String corpo = "{\"versao\":" + versao(tentativa.processo().getId()) + ",\"sumario\":\"Resumo do ato.\","
                + "\"dataAssinatura\":\"" + java.time.LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\","
                + "\"tipoInformacao\":\"TEXTO_INTEGRAL\",\"dataLimite\":\"2030-01-31\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501000952", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"))
                .andExpect(jsonPath("$.dataLimite").doesNotExist());

        mockMvc.perform(get("/api/v1/processos/" + tentativa.processo().getId())
                        .with(jwtComEntidadeEUtilizadorReal("501000952", tentativa.autorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataLimite").doesNotExist());
    }

    @Test
    void deveDevolver400AoSubmeterComTipoInformacaoForaDoEnum() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-830", "501000830");
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(versao(tentativa.processo().getId()), "Resumo do ato.", java.time.LocalDate.now(),
                "Norma Legal X", "PARCIAL", null, null);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501000830", tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
    }

    @Test
    void deveDevolver400AoSubmeterComNormaLegalAcimaDoLimite() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-831", "501000831");
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(versao(tentativa.processo().getId()), "Resumo do ato.", java.time.LocalDate.now(),
                "n".repeat(501), "TEXTO_INTEGRAL", null, null);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501000831", tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("normaLegal"));
    }

    private String dadosIncmCompletos(String serieDr) {
        String comuns = "\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + java.time.LocalDate.now() + "\","
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"],\"entidadeEmitente\":\"Camara Municipal de Teste\"";
        return "PRIMEIRA".equals(serieDr)
                ? comuns + ",\"publicarSuplemento\":true,\"dataSuplemento\":\"" + java.time.LocalDate.now() + "\""
                : comuns + ",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";
    }

    private void associarTipoDeAto(Processo processo, long idIncmSerie2) {
        TipoAto tipoAto = new TipoAto("ATO-" + idIncmSerie2, "Ato " + idIncmSerie2, 1);
        tipoAto.setIncmActTypeIdSerie2(idIncmSerie2);
        tipoAtoRepository.saveAndFlush(tipoAto);
        processo.setTipoAto(tipoAto);
        processoRepository.saveAndFlush(processo);
    }

    @ParameterizedTest
    @CsvSource({"SEGUNDA_H,8711,501008711", "SEGUNDA_C,8712,501008712", "PRIMEIRA,8713,501008713"})
    void devePodeSubmeterSerVerdadeiroSoQuandoOSubmeterJaNaoRecusaOsDadosIncm(String serieDr, String sufixo, String nif) throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-" + sufixo, nif, serieDr);
        UUID id = tentativa.processo().getId();
        RequestPostProcessor autor = jwtComEntidadeEUtilizadorReal(nif, tentativa.autorId());
        String dados = dadosIncmCompletos(serieDr);

        mockMvc.perform(get("/api/v1/processos/" + id).with(autor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + dados + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + id).with(autor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(true));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/submeter")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + dados + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        mockMvc.perform(get("/api/v1/processos/" + id).with(autor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));
    }

    @Test
    void deveDevolverPodeSubmeterFalsoAUmColegaDaEntidadeQueNaoEOAutorMesmoComOsDadosCompletos() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-8771", "501008771");
        Processo processo = tentativa.processo();
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(java.time.LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processoRepository.saveAndFlush(processo);
        String url = "/api/v1/processos/" + processo.getId();
        UUID colega = criarUtilizadorIncm("colega8771@cm-8771.pt");

        mockMvc.perform(get(url).with(jwtComEntidadeEUtilizadorReal("501008771", tentativa.autorId())))
                .andExpect(jsonPath("$.podeSubmeter").value(true));

        mockMvc.perform(get(url).with(jwtComEntidadeEUtilizadorReal("501008771", colega)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));
    }

    @Test
    void deveDevolverPodeSubmeterFalsoEDadosIncmEmFaltaQuandoFaltaANormaLegal() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-8731", "501008731");
        UUID id = tentativa.processo().getId();
        RequestPostProcessor autor = jwtComEntidadeEUtilizadorReal("501008731", tentativa.autorId());
        String semNormaLegal = "\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + java.time.LocalDate.now()
                + "\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";
        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + semNormaLegal + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + id).with(autor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/submeter")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + semNormaLegal + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"))
                .andExpect(jsonPath("$.campos[0].path").value("normaLegal"));
    }

    @Test
    void deveDevolverPodeSubmeterFalsoAUmTecnicoMesmoComOsDadosCompletos() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-8741", "501008741");
        Processo processo = tentativa.processo();
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(java.time.LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processoRepository.saveAndFlush(processo);
        String url = "/api/v1/processos/" + processo.getId();

        mockMvc.perform(get(url).with(jwtComEntidadeEUtilizadorReal("501008741", tentativa.autorId())))
                .andExpect(jsonPath("$.podeSubmeter").value(true));

        mockMvc.perform(get(url).with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));
    }

    @Test
    void deveDevolver400ValidacaoAoGravarESubmeterExtratoQuandoOTipoDeAtoNaoOPermite() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-8751", "501008751");
        associarTipoDeAto(tentativa.processo(), 8751L);
        UUID id = tentativa.processo().getId();
        RequestPostProcessor autor = jwtDeAutorComAtosAutorizados("501008751", tentativa.autorId(), 8751L, false);
        String extrato = dadosIncmCompletos("SEGUNDA_H").replace("TEXTO_INTEGRAL", "EXTRATO");

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + extrato + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/submeter")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + extrato + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));

        Processo naBase = processoRepository.findById(id).orElseThrow();
        assertThat(naBase.getTipoInformacao()).isNull();
        assertThat(naBase.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveAceitarExtratoAoGravarESubmeterQuandoOTipoDeAtoOPermite() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-SUB-8761", "501008761");
        associarTipoDeAto(tentativa.processo(), 8761L);
        UUID id = tentativa.processo().getId();
        RequestPostProcessor autor = jwtDeAutorComAtosAutorizados("501008761", tentativa.autorId(), 8761L, true);
        String extrato = dadosIncmCompletos("SEGUNDA_H").replace("TEXTO_INTEGRAL", "EXTRATO");

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + extrato + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + id).with(autor))
                .andExpect(jsonPath("$.tipoInformacao").value("EXTRATO"))
                .andExpect(jsonPath("$.podeSubmeter").value(true));

        mockMvc.perform(patch("/api/v1/processos/" + id + "/submeter")
                        .with(autor)
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "," + extrato + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveDevolver409AoResgatarQuandoNaoEstaEnviadoDgt() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-804", "501000804");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/resgatar")
                        .with(jwtComEntidadeEUtilizadorReal("501000804", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    @Test
    void deveDevolver200AoCapturarQuandoEnviadoDgt() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-901", "501000901");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_DGT"));
    }

    @Test
    void deveDevolver200AoCapturarQuandoTecnico() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-902", "501000902");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_DGT"));
    }

    @Test
    void deveDevolver200AoValidarERejeitarE403AoAnularQuandoTecnico() throws Exception {
        TentativaExistente paraValidar = criarTentativaExistente("PDM-4601", "501004601");
        TentativaExistente paraRejeitar = criarTentativaExistente("PDM-4602", "501004602");
        TentativaExistente paraAnular = criarTentativaExistente("PDM-4603", "501004603");
        documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(
                paraValidar.processo().getInstrumentoProcedimentoConfig()).forEach(item -> {
            item.setAtivo(false);
            documentoConfigRepository.saveAndFlush(item);
        });
        for (TentativaExistente tentativa : List.of(paraValidar, paraRejeitar, paraAnular)) {
            tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
            processoRepository.saveAndFlush(tentativa.processo());
        }
        comItemRejeitadoNoRascunho(paraRejeitar);

        mockMvc.perform(patch("/api/v1/processos/" + paraValidar.processo().getId() + "/validar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(paraValidar.processo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));
        Utilizador tecnico = new Utilizador("Técnico 4602", "tecnico4602@dgt.pt", "AD_DGT");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        UUID tecnicoId = utilizadorRepository.saveAndFlush(tecnico).getId();
        mockMvc.perform(patch("/api/v1/processos/" + paraRejeitar.processo().getId() + "/rejeitar")
                        .with(jwtComPerfilInterno(tecnicoId, "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RejeitarProcessoRequest(versao(paraRejeitar.processo().getId()), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REJEITADO_DGT"));
        mockMvc.perform(patch("/api/v1/processos/" + paraAnular.processo().getId() + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new AnularProcessoRequest(versao(paraAnular.processo().getId()), "motivo"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        assertThat(processoRepository.findById(paraAnular.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
    }

    @Test
    void deveDevolver200AoAnularComMotivoQuandoMaster() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-4604", "501004604");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new AnularProcessoRequest(versao(tentativa.processo().getId()), "Pedido da entidade."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));
    }

    private TentativaExistente criarTentativaNoEstado(String codigo, String nif, EstadoProcesso estado) {
        TentativaExistente tentativa = criarTentativaExistente(codigo, nif);
        tentativa.processo().setEstado(estado);
        processoRepository.saveAndFlush(tentativa.processo());
        return tentativa;
    }

    private ResultActions anularComoMaster(UUID processoId, String motivo) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processoId + "/anular")
                .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(processoId), motivo))));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "EM_VALIDACAO_DGT", "VALIDADO_DGT"})
    void deveAnularEAuditarOEstadoDeOrigemQuandoMaster(EstadoProcesso origem) throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AN" + origem.ordinal(), "50114760" + origem.ordinal(), origem);
        UUID id = tentativa.processo().getId();

        mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", hasItem("anular")));
        mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("anular"))));

        anularComoMaster(id, "Procedimento errado.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        Auditoria registo = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", id.toString()).stream()
                .filter(linha -> "ANULAR".equals(linha.getAcao())).findFirst().orElseThrow();
        assertThat(registo.getEstadoAntes()).isEqualTo(origem.name());
        assertThat(registo.getEstadoDepois()).isEqualTo("ANULADO");
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).get("motivo").asText()).isEqualTo("Procedimento errado.");
        assertThat(dinamicaRepository.findById(tentativa.dinamica().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDinamica.EM_CURSO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class,
            names = {"ENVIADO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO", "ANULADO", "ANULADO_INCM", "REJEITADO_DGT"})
    void deveDevolver409AoAnularForaDosEstadosAnulaveis(EstadoProcesso estado) throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AR" + estado.ordinal(), "50114770" + estado.ordinal(), estado);
        UUID id = tentativa.processo().getId();

        mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("anular"))));
        anularComoMaster(id, "motivo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(estado);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "VALIDADO_DGT"})
    void deveDevolver403AoAnularQuandoTecnico(EstadoProcesso origem) throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AT" + origem.ordinal(), "50114780" + origem.ordinal(), origem);
        UUID id = tentativa.processo().getId();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(id), "motivo"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(patch("/api/v1/processos/" + id + "/anular")
                        .with(jwtComEntidadeEUtilizadorReal("50114780" + origem.ordinal(), tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(id), "motivo"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(origem);
    }

    @Test
    void deveDevolver400AoAnularComMotivoSoComEspacos() throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AV1", "501147901", EstadoProcesso.ENVIADO_DGT);

        anularComoMaster(tentativa.processo().getId(), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolverVersaoDesatualizadaAoAnularDepoisDeOAutorResgatar() throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AD1", "501147902", EstadoProcesso.ENVIADO_DGT);
        UUID id = tentativa.processo().getId();
        long versaoLidaPeloMaster = versao(id);

        mockMvc.perform(patch("/api/v1/processos/" + id + "/resgatar")
                        .with(jwtComEntidadeEUtilizadorReal("501147902", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/processos/" + id + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versaoLidaPeloMaster, "motivo"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarEnviarIncmDepoisDeAnularUmValidadoDgt() throws Exception {
        TentativaExistente tentativa = criarTentativaNoEstado("PDM-AD2", "501147903", EstadoProcesso.VALIDADO_DGT);
        UUID id = tentativa.processo().getId();
        anularComoMaster(id, "Pedido da entidade.").andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        verify(envioIncmService, never()).enviar(any(), any());
        assertThat(dinamicaRepository.findById(tentativa.dinamica().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDinamica.EM_CURSO);
    }

    /** As transições falham na Camada 3 (403), antes da Camada 4: o estado de cada processo é o de partida da ação. */
    @Test
    void deveDevolver403EmTodasAsTransicoesQuandoVisitante() throws Exception {
        TentativaExistente enviado = criarTentativaExistente("PDM-4605", "501004605");
        enviado.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(enviado.processo());
        TentativaExistente emValidacao = criarTentativaExistente("PDM-4606", "501004606");
        emValidacao.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(emValidacao.processo());
        ProcessoValidadoDgt validado = criarProcessoValidadoDgt("PDM-4607", "501004607", "PRIMEIRA");
        String json = "application/json";
        long versaoEnviado = versao(enviado.processo().getId());
        long versaoEmValidacao = versao(emValidacao.processo().getId());
        long versaoValidado = versao(validado.processo().getId());

        mockMvc.perform(patch("/api/v1/processos/" + enviado.processo().getId() + "/capturar").with(jwtVisitante())
                        .contentType(json).content(corpoVersao(enviado.processo().getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/processos/" + enviado.processo().getId() + "/resgatar").with(jwtVisitante())
                        .contentType(json).content(corpoVersao(enviado.processo().getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/processos/" + emValidacao.processo().getId() + "/validar").with(jwtVisitante())
                        .contentType(json).content(corpoVersao(emValidacao.processo().getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/processos/" + emValidacao.processo().getId() + "/rejeitar").with(jwtVisitante())
                        .contentType(json).content(objectMapper.writeValueAsString(new RejeitarProcessoRequest(versaoEmValidacao, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/processos/" + emValidacao.processo().getId() + "/anular").with(jwtVisitante())
                        .contentType(json).content(objectMapper.writeValueAsString(new AnularProcessoRequest(versaoEmValidacao, "motivo"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/processos/" + validado.processo().getId() + "/enviar-pcm").with(jwtVisitante())
                        .contentType(json).content(objectMapper.writeValueAsString(new EnviarPcmRequest(versaoValidado, UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PCM_ATOR_NAO_AUTORIZADO"));
        mockMvc.perform(patch("/api/v1/processos/" + validado.processo().getId() + "/enviar-incm").with(jwtVisitante())
                        .contentType(json).content(corpoVersao(validado.processo().getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        verify(envioIncmService, never()).enviar(any(), any());

        assertThat(versao(enviado.processo().getId())).isEqualTo(versaoEnviado);

        assertThat(processoRepository.findById(enviado.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(processoRepository.findById(emValidacao.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(processoRepository.findById(validado.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    @Test
    void deveListarEConsultarProcessosDeVariasEntidadesQuandoVisitante() throws Exception {
        TentativaExistente a = criarTentativaExistente("PDM-4608", "501004608");
        TentativaExistente b = criarTentativaExistente("PDM-4609", "501004609");

        mockMvc.perform(get("/api/v1/processos").param("designacaoInstrumento", "PDM-460").with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.entidadeId==" + a.processo().getEntidade().getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.entidadeId==" + b.processo().getEntidade().getId() + ")]").exists());
        mockMvc.perform(get("/api/v1/processos/" + a.processo().getId()).with(jwtVisitante()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + b.processo().getId() + "/historico").with(jwtVisitante()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + b.processo().getId() + "/estatisticas").with(jwtVisitante()))
                .andExpect(status().isOk());
        mockMvc.perform(get(URL + "/" + a.instrumento().getId()).with(jwtVisitante()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor jwtVisitante() {
        return jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE");
    }

    @Test
    void deveDevolver400AoAnularSemMotivo() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-905", "501000905");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(tentativa.processo().getId()), ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    /** Duas cópias lidas em separado: a segunda gravação tem de falhar no `@Version` real, não num mock. */
    @Test
    void deveLancarConflitoDeVersaoRealQuandoDuasCopiasDaMesmaTentativaSaoGravadas() {
        TentativaExistente tentativa = criarTentativaExistente("PDM-906", "501000906");
        UUID processoId = tentativa.processo().getId();

        Processo copiaA = processoRepository.findById(processoId).orElseThrow();
        Processo copiaB = processoRepository.findById(processoId).orElseThrow();

        copiaA.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(copiaA);

        copiaB.setEstado(EstadoProcesso.ENVIADO_DGT);
        assertThatThrownBy(() -> processoRepository.saveAndFlush(copiaB))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    // --- Ações de exceção da DGT ---

    @Test
    void deveDevolver403AoMasterEditarFormulario() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1005", "501001005");
        tentativa.processo().setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"corrigido\"}}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveDevolver200AoReatribuirAutorSemMotivoEAuditarOMasterSemMotivo() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1007", "501001007");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.autor1007@cm-1007.pt", tentativa.processo());
        UUID masterId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(masterId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(processoId), novoUtilizadorId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.criadoPorId").value(novoUtilizadorId.toString()));

        Auditoria registo = registoDaReatribuicao(processoId);
        assertThat(registo.getUtilizadorId()).isEqualTo(masterId);
        JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
        assertThat(dados.get("autorAnteriorId").asText()).isEqualTo(tentativa.autorId().toString());
        assertThat(dados.get("novoAutorId").asText()).isEqualTo(novoUtilizadorId.toString());
        assertThat(dados.has("motivo")).isFalse();
    }

    @Test
    void umMotivoAindaEnviadoEAceiteENaoEGravado() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1009", "501001009");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.autor1009@cm-1009.pt", tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(processoId) + ",\"novoUtilizadorId\":\"" + novoUtilizadorId
                                + "\",\"motivo\":\"Pedido da entidade.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadoPorId").value(novoUtilizadorId.toString()));

        assertThat(registoDaReatribuicao(processoId).getDadosAlteracao()).doesNotContain("motivo", "Pedido da entidade.");
    }

    @Test
    void deveTransferirACamada3bParaONovoAutorAposReatribuicao() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1011", "501001011");
        UUID novoAutorId = criarUtilizadorDaEntidade("novo.autor1011@cm-1011.pt", tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(tentativa.processo().getId()), novoAutorId, null))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501001011", tentativa.autorId()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        mockMvc.perform(put("/api/v1/processos/" + tentativa.processo().getId() + "/dados-formulario")
                        .with(jwtComEntidadeEUtilizadorReal("501001011", novoAutorId))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(tentativa.processo().getId()) + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"valor\"}}}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"ENVIADO_DGT", "REJEITADO_DGT", "ANULADO_INCM"})
    void deveDevolver200AoReatribuirNosOutrosEstadosEmQueOOwnerAge(EstadoProcesso estado) throws Exception {
        TentativaExistente tentativa = tentativaNoEstado(estado);
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.r407." + estado.ordinal() + "@cm-r407.pt", tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(processoId), novoUtilizadorId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estado.name()))
                .andExpect(jsonPath("$.criadoPorId").value(novoUtilizadorId.toString()));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class,
            names = {"EM_VALIDACAO_DGT", "VALIDADO_DGT", "ENVIADO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO", "ANULADO"})
    void deveDevolver409AoReatribuirNosEstadosEmQueOOwnerNaoAge(EstadoProcesso estado) throws Exception {
        TentativaExistente tentativa = tentativaNoEstado(estado);
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.r407." + estado.ordinal() + "@cm-r407.pt", tentativa.processo());
        long versaoAntes = versao(processoId);
        Notificacao pedido = criarPedidoDeApoio(tentativa, TipoNotificacao.PEDIDO_APOIO, tentativa.processo(),
                EstadoNotificacao.ATIVA);

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versaoAntes, novoUtilizadorId,
                                pedido.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("O processo não pode ser transferido neste estado."));

        Processo depois = processoRepository.findById(processoId).orElseThrow();
        assertThat(depois.getCriadoPor().getId()).isEqualTo(tentativa.autorId());
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()))
                .noneMatch(linha -> "REATRIBUIR_AUTOR".equals(linha.getAcao()));
        Notificacao pedidoDepois = notificacaoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(pedidoDepois.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(pedidoDepois.getResolvidoEm()).isNull();
    }

    // --- reatribuir-autor com o pedido de apoio da entidade ---

    @Test
    void deveResolverOPedidoDeApoioDoProcessoAoReatribuirEAuditarOPedidoApoioId() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-1", "507498001");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.r498.1@cm-r498.pt", tentativa.processo());
        Notificacao pedido = criarPedidoDeApoio(tentativa, TipoNotificacao.PEDIDO_APOIO, tentativa.processo(),
                EstadoNotificacao.ATIVA);
        UUID masterId = UUID.randomUUID();
        Instant antes = Instant.now();

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(masterId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(processoId),
                                novoUtilizadorId, pedido.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadoPorId").value(novoUtilizadorId.toString()));

        Notificacao depois = notificacaoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(depois.getResolvidoEm()).isAfterOrEqualTo(antes.minusSeconds(1));
        Auditoria registo = registoDaReatribuicao(processoId);
        assertThat(registo.getUtilizadorId()).isEqualTo(masterId);
        JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
        assertThat(dados.get("pedidoApoioId").asText()).isEqualTo(pedido.getId().toString());
        assertThat(dados.get("novoAutorId").asText()).isEqualTo(novoUtilizadorId.toString());
        assertThat(dados.has("motivo")).isFalse();
    }

    @Test
    void deveResolverUmPedidoDeApoioGeralAoReatribuir() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-2", "507498002");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.r498.2@cm-r498.pt", tentativa.processo());
        Notificacao geral = criarPedidoDeApoio(tentativa, TipoNotificacao.PEDIDO_APOIO, null, EstadoNotificacao.ATIVA);

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(processoId),
                                novoUtilizadorId, geral.getId()))))
                .andExpect(status().isOk());

        Notificacao depois = notificacaoRepository.findById(geral.getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(depois.getResolvidoEm()).isNotNull();
        assertThat(objectMapper.readTree(registoDaReatribuicao(processoId).getDadosAlteracao())
                .get("pedidoApoioId").asText()).isEqualTo(geral.getId().toString());
    }

    @Test
    void deveDevolver400AoReatribuirComUmPedidoDeApoioJaResolvidoESemMudarNada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-3", "507498003");
        Notificacao resolvido = criarPedidoDeApoio(tentativa, TipoNotificacao.PEDIDO_APOIO, tentativa.processo(),
                EstadoNotificacao.RESOLVIDA);

        assertReatribuicaoRecusadaPeloPedido(tentativa, resolvido, "O pedido de apoio indicado não está pendente.");
    }

    @Test
    void deveDevolver400AoReatribuirComUmaNotificacaoDeOutroTipoESemMudarNada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-4", "507498004");
        Notificacao regularizacao = criarPedidoDeApoio(tentativa, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO,
                tentativa.processo(), EstadoNotificacao.ATIVA);

        assertReatribuicaoRecusadaPeloPedido(tentativa, regularizacao, "O pedido de apoio indicado não está pendente.");
    }

    @Test
    void deveDevolver400AoReatribuirComUmPedidoDeApoioDeOutroProcessoESemMudarNada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-5", "507498005");
        TentativaExistente outra = criarTentativaExistente("R498-6", "507498006");
        Notificacao deOutroProcesso = criarPedidoDeApoio(outra, TipoNotificacao.PEDIDO_APOIO, outra.processo(),
                EstadoNotificacao.ATIVA);

        assertReatribuicaoRecusadaPeloPedido(tentativa, deOutroProcesso, "O pedido de apoio é de outro processo.");
    }

    @Test
    void deveDevolver404AoReatribuirComUmPedidoDeApoioInexistenteESemMudarNada() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("R498-7", "507498007");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.r498.7@cm-r498.pt", tentativa.processo());
        long versaoAntes = versao(processoId);

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versaoAntes, novoUtilizadorId,
                                UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Pedido de apoio não encontrado."));

        assertProcessoSemReatribuicao(tentativa, versaoAntes);
    }

    private void assertReatribuicaoRecusadaPeloPedido(TentativaExistente tentativa, Notificacao pedido, String mensagem)
            throws Exception {
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo." + processoId + "@cm-r498.pt", tentativa.processo());
        long versaoAntes = versao(processoId);
        Notificacao antes = notificacaoRepository.findById(pedido.getId()).orElseThrow();

        mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versaoAntes, novoUtilizadorId,
                                pedido.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(mensagem))
                .andExpect(jsonPath("$.campos[0].path").value("pedidoApoioId"));

        assertProcessoSemReatribuicao(tentativa, versaoAntes);
        Notificacao depois = notificacaoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(antes.getEstado());
        assertThat(depois.getResolvidoEm()).isEqualTo(antes.getResolvidoEm());
    }

    private void assertProcessoSemReatribuicao(TentativaExistente tentativa, long versaoAntes) {
        UUID processoId = tentativa.processo().getId();
        Processo depois = processoRepository.findById(processoId).orElseThrow();
        assertThat(depois.getCriadoPor().getId()).isEqualTo(tentativa.autorId());
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()))
                .noneMatch(linha -> "REATRIBUIR_AUTOR".equals(linha.getAcao()));
    }

    /** Como a entidade o cria: dirigido aos Master, com o autor do processo em `criado_por`. */
    private Notificacao criarPedidoDeApoio(TentativaExistente tentativa, TipoNotificacao tipo, Processo doProcesso,
                                           EstadoNotificacao estado) {
        Utilizador autor = utilizadorRepository.findById(tentativa.autorId()).orElseThrow();
        Notificacao pedido = new Notificacao(tipo, "Transferência de autoria", "Pedimos a transferência do processo.",
                autor);
        pedido.setPerfilDestino(PerfilInterno.MASTER);
        pedido.setProcesso(doProcesso);
        pedido.setEstado(estado);
        if (estado == EstadoNotificacao.RESOLVIDA) {
            pedido.setResolvidoEm(Instant.parse("2026-11-01T10:00:00Z"));
        }
        return notificacaoRepository.saveAndFlush(pedido);
    }

    /** Sem vínculo à entidade: a `auditoria` do admin sobre a entidade não conta como pertença. */
    @Test
    void deveDevolver400AoReatribuirAMasterQueSoAtuouSobAEntidade() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1012", "501001012");
        UUID adminId = criarUtilizadorMaster("admin.1012@dgt.pt");
        auditoriaService.registar("PROCESSO", tentativa.processo().getId().toString(), "VALIDACAO", adminId,
                tentativa.processo().getEntidade().getId(), null, null);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(tentativa.processo().getId()), adminId, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("novoUtilizadorId"));
        assertThat(processoRepository.findById(tentativa.processo().getId()).orElseThrow().getCriadoPor().getId())
                .isEqualTo(tentativa.autorId());
    }

    @Test
    void deveDevolver400AoReatribuirAUtilizadorDeOutraEntidade() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1013", "501001013");
        TentativaExistente outra = criarTentativaExistente("PDM-1014", "501001014");
        UUID deOutraEntidade = criarUtilizadorDaEntidade("outra.entidade1014@cm-1014.pt", outra.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(tentativa.processo().getId()), deOutraEntidade, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("O utilizador indicado não pertence à entidade do Instrumento."))
                .andExpect(jsonPath("$.campos[0].path").value("novoUtilizadorId"));
    }

    /** A desativação saiu: quem estava desativado antes da migração recebe a autoria como os outros. */
    @Test
    void deveDevolver200AoReatribuirAQuemEstavaDesativadoAntesDaMigracao() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1015", "501001015");
        UUID antesDesativadoId = criarUtilizadorDaEntidade("antes.desativado1015@cm-1015.pt", tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(tentativa.processo().getId()), antesDesativadoId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadoPorId").value(antesDesativadoId.toString()));
    }

    @Test
    void deveDevolver409AoReatribuirAoProprioAutor() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1016", "501001016");
        Utilizador autor = utilizadorRepository.findById(tentativa.autorId()).orElseThrow();
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(autor, tentativa.processo().getEntidade(), Instant.now()));

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/reatribuir-autor")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(tentativa.processo().getId()), tentativa.autorId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("O utilizador indicado já é o autor do Processo."));
    }

    @Test
    void deveDevolver403AoReatribuirQuandoTecnicoVisitanteOuUtilizadorExterno() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1010", "501001010");
        UUID processoId = tentativa.processo().getId();
        UUID novoUtilizadorId = criarUtilizadorDaEntidade("novo.autor1010@cm-1010.pt", tentativa.processo());
        String corpo = objectMapper.writeValueAsString(new ReatribuirAutorRequest(versao(processoId), novoUtilizadorId, null));

        for (RequestPostProcessor chamador : List.of(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"),
                jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE"),
                jwtComEntidadeEUtilizadorReal("501001010", tentativa.autorId()))) {
            mockMvc.perform(patch("/api/v1/processos/" + processoId + "/reatribuir-autor")
                            .with(chamador)
                            .contentType("application/json")
                            .content(corpo))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
        assertThat(processoRepository.findById(processoId).orElseThrow().getCriadoPor().getId()).isEqualTo(tentativa.autorId());
    }

    private TentativaExistente tentativaNoEstado(EstadoProcesso estado) {
        TentativaExistente tentativa = criarTentativaExistente("R407-" + estado.ordinal(), "50740700" + estado.ordinal());
        tentativa.processo().setEstado(estado);
        processoRepository.saveAndFlush(tentativa.processo());
        return tentativa;
    }

    private Auditoria registoDaReatribuicao(UUID processoId) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()).stream()
                .filter(linha -> "REATRIBUIR_AUTOR".equals(linha.getAcao())).findFirst().orElseThrow();
    }

    // --- Registo externo de Procedimento já publicado no DR ---

    private record DinamicaExistente(Instrumento instrumento, Dinamica dinamica, InstrumentoProcedimentoConfig procedimentoConfig,
                                     Entidade entidade) {
    }

    /** Dinâmica em curso sem nenhum Processo. */
    private DinamicaExistente criarDinamicaExistente(String codigo, String nif) {
        return criarDinamicaExistente(codigo, nif, criarCadeiaComPermissaoParaEntidade(codigo));
    }

    private DinamicaExistente criarDinamicaExistente(String codigo, String nif, CadeiaConfigurada cadeia) {
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento " + codigo, "instrumento" + codigo + "@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/" + codigo, criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Alteração do PDM", criadoPor));
        return new DinamicaExistente(instrumento, dinamica, cadeia.procedimentoConfig(), entidade);
    }

    @Test
    void deveDevolver201AoRegistarProcessoExterno() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1101", "501001101");
        UUID adminId = criarUtilizadorMaster("admin1101@dgt.pt");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1101", java.time.LocalDate.now(),
                "Publicado antes da plataforma existir.", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.origemDados").value("REGISTO_EXTERNO"))
                .andExpect(jsonPath("$.ocorrencia").value(1))
                .andExpect(jsonPath("$.documentosCopiados").value(0));
    }

    @Test
    void naoDeveCalcularADataDeCaducidadeAoRegistarExternamenteADecisaoDeInicio() throws Exception {
        TipoProcedimento decisaoInicio = tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> tipo.getCodigo().equals("DECISAO_INICIO")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início")));
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1108", "501001108",
                criarCadeiaComPermissaoParaEntidade("PDM-1108", "SEGUNDA_H", decisaoInicio));
        UUID adminId = criarUtilizadorMaster("admin1108@dgt.pt");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1108", java.time.LocalDate.now(), "motivo", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated());

        // O pedido não traz o prazo do Texto do ato: a data fica por calcular.
        assertThat(jdbcTemplate.queryForObject("SELECT data_caducidade FROM dinamica WHERE id = ?", java.time.LocalDate.class,
                dinamicaExistente.dinamica().getId())).isNull();
    }

    @Test
    void deveDevolver403AoRegistarExternoQuandoTecnico() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1102", "501001102");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1102", java.time.LocalDate.now(), "motivo", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver403AoRegistarExternoQuandoEntidadeExterna() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1103", "501001103");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1103", java.time.LocalDate.now(), "motivo", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComEntidade("501001103"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver400AoRegistarExternoSemNumeroDr() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1104", "501001104");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "", java.time.LocalDate.now(), "motivo", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400AoRegistarExternoSemDataPublicacaoDr() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1105", "501001105");

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content("{\"instrumentoProcedimentoConfigId\":" + dinamicaExistente.procedimentoConfig().getId()
                                + ",\"numeroDr\":\"2026/DR/1105\",\"motivo\":\"motivo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400AoRegistarExternoSemMotivo() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1106", "501001106");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1106", java.time.LocalDate.now(), "", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver201ComNumeroTentativaSeguinteQuandoJaExisteUmaTentativa() throws Exception {
        DinamicaExistente dinamicaExistente = criarDinamicaExistente("PDM-1107", "501001107");
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor 1107", "autor1107@cm-1107.pt", "INCM"));
        processoRepository.saveAndFlush(new Processo(dinamicaExistente.dinamica(), dinamicaExistente.procedimentoConfig(), autor, dinamicaExistente.entidade()));
        UUID adminId = criarUtilizadorMaster("admin1107@dgt.pt");
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                dinamicaExistente.procedimentoConfig().getId(), dinamicaExistente.entidade().getId(), "2026/DR/1107", java.time.LocalDate.now(), "motivo", null);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamicaExistente.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrencia").value(2));
    }

    // --- Fluxo de Procedimentos de 1.ª série — encaminhamento para a PCM ---

    private record ProcessoValidadoDgt(Processo processo, Entidade entidade) {
    }

    private ProcessoValidadoDgt criarProcessoValidadoDgt(String codigo, String nif, String serieDr) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, serieDr));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processo = processoRepository.saveAndFlush(processo);
        return new ProcessoValidadoDgt(processo, entidade);
    }

    /** Utilizador INCM com vínculo a uma entidade de tipo PCM, como o deixa o login. */
    private UUID criarUtilizadorPcm(String email, String nif, boolean entidadeAtiva) {
        Entidade pcm = EntidadeTeste.nova(nif, "PCM " + nif, TipoEntidade.PCM);
        pcm.setAtivo(entidadeAtiva);
        pcm = entidadeRepository.saveAndFlush(pcm);
        Utilizador utilizador = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador PCM", email, "INCM"));
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, pcm, Instant.now()));
        return utilizador.getId();
    }

    private UUID criarUtilizadorPcm(String email, String nif) {
        return criarUtilizadorPcm(email, nif, true);
    }

    /** Sem `documento_config`: um documento que não é obrigatório. */
    private void documentoNoEstado(Processo processo, EstadoValidacao estado) {
        ProcessoDocumento documento = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf", "f.pdf",
                "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        documento.setEstadoValidacao(estado);
        processoDocumentoRepository.saveAndFlush(documento);
    }

    private org.springframework.test.web.servlet.ResultActions enviarPcm(UUID processoId, long versao, UUID utilizadorId,
                                                                      RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processoId + "/enviar-pcm").with(jwt)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao, utilizadorId))));
    }

    private List<Auditoria> registosEnviarPcm(UUID processoId) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()).stream()
                .filter(linha -> "ENVIAR_PCM".equals(linha.getAcao())).toList();
    }

    /** O pedido recusado não grava nada: sem atribuição nova, a mesma `versao` e sem linha `ENVIAR_PCM`. */
    private void semAlteracoes(UUID processoId, long versaoAntes, UUID atribuidoAntes) {
        Processo depois = processoRepository.findById(processoId).orElseThrow();
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(depois.getPcmAtribuidoA() == null ? null : depois.getPcmAtribuidoA().getId()).isEqualTo(atribuidoAntes);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        if (atribuidoAntes == null) {
            assertThat(registosEnviarPcm(processoId)).isEmpty();
        }
    }

    @Test
    void deveEnviarParaPcmComoTecnicoGravarAAtribuicaoEAuditarSemMudarOEstado() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1781", "501001781", "PRIMEIRA");
        UUID id = tentativa.processo().getId();
        documentoNoEstado(tentativa.processo(), EstadoValidacao.VALIDADO);
        documentoNoEstado(tentativa.processo(), EstadoValidacao.SUBSTITUIDO);
        UUID pcmId = criarUtilizadorPcm("pcm1781@pcm.gov.pt", "600001781");
        UUID tecnicoId = UUID.randomUUID();

        enviarPcm(id, versao(id), pcmId, jwtComPerfilInterno(tecnicoId, "TECNICO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"))
                .andExpect(jsonPath("$.pcmAtribuidoAId").value(pcmId.toString()));

        Processo depois = processoRepository.findById(id).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(depois.getPcmAtribuidoA().getId()).isEqualTo(pcmId);
        List<Auditoria> registos = registosEnviarPcm(id);
        assertThat(registos).hasSize(1);
        assertThat(registos.get(0).getUtilizadorId()).isEqualTo(tecnicoId);
        assertThat(objectMapper.readTree(registos.get(0).getDadosAlteracao()).get("pcmAtribuidoAId").asText())
                .isEqualTo(pcmId.toString());
    }

    @Test
    void deveDevolver403PcmAtorNaoAutorizadoAoUtilizadorExternoSemMudarNada() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1782", "501001782", "PRIMEIRA");
        UUID id = tentativa.processo().getId();
        long versaoAntes = versao(id);
        UUID pcmId = criarUtilizadorPcm("pcm1782@pcm.gov.pt", "600001782");

        enviarPcm(id, versaoAntes, pcmId, jwtComEntidade("501001782"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PCM_ATOR_NAO_AUTORIZADO"));
        enviarPcm(id, versaoAntes, pcmId, jwtVisitante())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PCM_ATOR_NAO_AUTORIZADO"));

        semAlteracoes(id, versaoAntes, null);
    }

    @Test
    void deveDevolver409PcmEstadoInvalidoEmValidacaoDgt() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1783", "501001783", "PRIMEIRA");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();
        UUID pcmId = criarUtilizadorPcm("pcm1783@pcm.gov.pt", "600001783");

        enviarPcm(id, versao(id), pcmId, jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_ESTADO_INVALIDO"));

        assertThat(processoRepository.findById(id).orElseThrow().getPcmAtribuidoA()).isNull();
    }

    @Test
    void deveDevolver409PcmJaAtribuidoSemMudarAAtribuicao() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1784", "501001784", "PRIMEIRA");
        UUID id = tentativa.processo().getId();
        UUID primeiro = criarUtilizadorPcm("pcm1784a@pcm.gov.pt", "600001784");
        UUID segundo = criarUtilizadorPcm("pcm1784b@pcm.gov.pt", "600001785");
        enviarPcm(id, versao(id), primeiro, jwtComPerfilInterno(UUID.randomUUID(), "MASTER")).andExpect(status().isOk());
        long versaoAntes = versao(id);

        enviarPcm(id, versaoAntes, segundo, jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_JA_ATRIBUIDO"))
                .andExpect(jsonPath("$.mensagem").value("O processo já está com a PCM."));

        semAlteracoes(id, versaoAntes, primeiro);
        assertThat(registosEnviarPcm(id)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoValidacao.class, names = {"POR_VALIDAR", "REJEITADO"})
    void deveDevolver409PcmDocumentosPorValidarComUmDocumentoNaoObrigatorioPorValidar(EstadoValidacao estado) throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1786" + estado.ordinal(), "50117860" + estado.ordinal(), "PRIMEIRA");
        UUID id = tentativa.processo().getId();
        documentoNoEstado(tentativa.processo(), EstadoValidacao.VALIDADO);
        documentoNoEstado(tentativa.processo(), estado);
        long versaoAntes = versao(id);
        UUID pcmId = criarUtilizadorPcm("pcm1786" + estado.ordinal() + "@pcm.gov.pt", "60017860" + estado.ordinal());

        enviarPcm(id, versaoAntes, pcmId, jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_DOCUMENTOS_POR_VALIDAR"))
                .andExpect(jsonPath("$.mensagem").value("Existem documentos por validar."));

        semAlteracoes(id, versaoAntes, null);
    }

    @Test
    void deveDevolver400PcmUtilizadorInvalidoNoCampoUtilizadorIdSemMudarNada() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1787", "501001787", "PRIMEIRA");
        UUID id = tentativa.processo().getId();
        long versaoAntes = versao(id);
        UUID daCamara = criarUtilizadorDaEntidade("cm1787@cm-1787.pt", tentativa.processo());
        UUID daPcmInativa = criarUtilizadorPcm("pcm1787@pcm.gov.pt", "600001787", false);
        UUID semVinculo = criarUtilizadorIncm("sv1787@exemplo.pt");

        for (UUID invalido : List.of(UUID.randomUUID(), daCamara, daPcmInativa, semVinculo)) {
            enviarPcm(id, versaoAntes, invalido, jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PCM_UTILIZADOR_INVALIDO"))
                    .andExpect(jsonPath("$.mensagem").value("O utilizador indicado não pertence à PCM."))
                    .andExpect(jsonPath("$.campos[0].path").value("utilizadorId"));
        }

        semAlteracoes(id, versaoAntes, null);
    }

    @Test
    void deveDevolver409VersaoDesatualizadaAoEnviarParaPcmAntesDasRegrasDeEstado() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1788", "501001788", "SEGUNDA_H");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());
        UUID id = tentativa.processo().getId();

        enviarPcm(id, versao(id) + 1, UUID.randomUUID(), jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
    }

    @Test
    void deveListarEnviarPcmNoDetalheSoComTodosOsDocumentosValidadosESemAtribuicao() throws Exception {
        ProcessoValidadoDgt pronto = criarProcessoValidadoDgt("PDM-1789", "501001789", "PRIMEIRA");
        documentoNoEstado(pronto.processo(), EstadoValidacao.VALIDADO);
        ProcessoValidadoDgt porValidar = criarProcessoValidadoDgt("PDM-1790", "501001790", "PRIMEIRA");
        documentoNoEstado(porValidar.processo(), EstadoValidacao.POR_VALIDAR);
        UUID tecnicoId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/processos/" + pronto.processo().getId()).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", hasItem("enviar-pcm")));
        mockMvc.perform(get("/api/v1/processos/" + porValidar.processo().getId()).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("enviar-pcm"))));

        UUID pcmId = criarUtilizadorPcm("pcm1789@pcm.gov.pt", "600001789");
        UUID id = pronto.processo().getId();
        enviarPcm(id, versao(id), pcmId, jwtComPerfilInterno(tecnicoId, "TECNICO")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("enviar-pcm"))));
    }

    @Test
    void deveDevolver200AoEnviarParaPcmQuandoSerieDrPrimeira() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1201", "501001201", "PRIMEIRA");
        UUID pcmId = criarUtilizadorPcm("pcm1201@dgterritorio.gov.pt", "600001201");

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-pcm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao(tentativa.processo().getId()), pcmId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"))
                .andExpect(jsonPath("$.pcmAtribuidoAId").value(pcmId.toString()));
    }

    @Test
    void deveDevolver409AoEnviarParaPcmQuandoSerieDrSegundaH() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1202", "501001202", "SEGUNDA_H");
        UUID pcmId = criarUtilizadorPcm("pcm1202@dgterritorio.gov.pt", "600001202");

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-pcm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao(tentativa.processo().getId()), pcmId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_SERIE_NAO_PRIMEIRA"));
    }

    @Test
    void deveDevolver200AoEnviarParaPcmQuandoTecnico() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1203", "501001203", "PRIMEIRA");
        UUID pcmId = criarUtilizadorPcm("pcm1203@dgterritorio.gov.pt", "600001203");

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-pcm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao(tentativa.processo().getId()), pcmId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pcmAtribuidoAId").value(pcmId.toString()));
    }

    @Test
    void deveDevolver200ComAnuladoIncmAoEnviarIncmComoMasterQuandoAIncmRejeita() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1204", "501001204", "SEGUNDA_H");
        envioLevaPara(tentativa.processo().getId(), EstadoProcesso.ANULADO_INCM);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO_INCM"));
    }

    @Test
    void deveDevolver404AoEnviarIncmComoPcmDeOutraEntidadeNoProcessoQueNaoLheFoiAtribuido() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1205", "501001205", "PRIMEIRA");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("999999205", "PCM 1205", TipoEntidade.PCM));

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComEntidadePcm("999999205"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isNotFound());

        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver404AoEnviarIncmComoPcmQuandoOPcmAtribuidoApontaParaOutro() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1206", "501001206", "PRIMEIRA");
        UUID outroPcmId = criarUtilizadorPcm("outro.pcm1206@dgterritorio.gov.pt", "600001206");
        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-pcm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao(tentativa.processo().getId()), outroPcmId))))
                .andExpect(status().isOk());
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("999999206", "PCM 1206", TipoEntidade.PCM));

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComEntidadePcm("999999206"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isNotFound());

        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver200ComEmValidacaoIncmAoEnviarIncmQuandoTecnico() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1207", "501001207", "SEGUNDA_H");
        envioLevaPara(tentativa.processo().getId(), EstadoProcesso.EM_VALIDACAO_INCM);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));
    }

    @Test
    void deveDevolver500IntegracaoIndisponivelEManterValidadoDgtQuandoAIncmEstaIndisponivel() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1210", "501001210", "SEGUNDA_H");
        UUID id = tentativa.processo().getId();
        doThrow(new IncmIndisponivelException("A INCM não respondeu ao envio do ato. Tente novamente mais tarde."))
                .when(envioIncmService).enviar(eq(id), any());

        mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("INTEGRACAO_INDISPONIVEL"));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    @Test
    void deveDevolver409VersaoDesatualizadaAoEnviarIncmComVersaoAntiga() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1211", "501001211", "SEGUNDA_H");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content("{\"versao\":" + (versao(id) + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver403AoEnviarIncmQuandoEntidadeSubmissora() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1208", "501001208", "SEGUNDA_H");

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComEntidade("501001208"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver404AoPcmEnviarIncmForaDeValidadoDgt() throws Exception {
        ProcessoValidadoDgt tentativa = criarProcessoValidadoDgt("PDM-1209", "501001209", "SEGUNDA_H");
        tentativa.processo().setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa.processo());

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/enviar-incm")
                        .with(jwtComEntidadePcm("999999209"))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isNotFound());

        verify(envioIncmService, never()).enviar(any(), any());
    }

    // --- Cascade de estados ---

    private record DinamicaComDoisObrigatorios(Instrumento instrumento, Dinamica dinamica,
                                                InstrumentoProcedimentoConfig ordem1, InstrumentoProcedimentoConfig ordem2, Utilizador autor,
                                                Entidade entidade) {
    }

    private DinamicaComDoisObrigatorios criarDinamicaComDoisObrigatorios(String codigo, String nif) {
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        return criarDinamicaComDoisObrigatorios(codigo, entidade, autor);
    }

    private DinamicaComDoisObrigatorios criarDinamicaComDoisObrigatorios(String codigo, Entidade entidade, Utilizador autor) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamicaElaboracao()));
        InstrumentoProcedimentoConfig ordem1= procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P1-" + codigo, "Procedimento 1")), 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P2-" + codigo, "Procedimento 2")), 2, "SEGUNDA_H"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        return new DinamicaComDoisObrigatorios(instrumento, dinamica, ordem1, ordem2, autor, entidade);
    }

    private void registarExternamente(DinamicaComDoisObrigatorios dc, InstrumentoProcedimentoConfig procedimento, UUID adminId)
            throws Exception {
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(
                procedimento.getId(), dc.entidade().getId(), "2026/DR/" + procedimento.getId(), java.time.LocalDate.now(), "motivo", null);
        mockMvc.perform(post("/api/v1/dinamicas/" + dc.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER")).contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated());
    }

    @Test
    void deveTerminarADinamicaQuandoOProcedimentoFinalEPublicadoSemOsAnterioresPublicados() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-6303", "501006303");
        UUID adminId = criarUtilizadorMaster("admin6303@dgt.pt");

        registarExternamente(dc, dc.ordem2(), adminId);

        Dinamica terminada = dinamicaRepository.findById(dc.dinamica().getId()).orElseThrow();
        assertThat(terminada.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(terminada.getDataConclusao()).isNotNull();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("DINAMICA", dc.dinamica().getId().toString()))
                .extracting("acao", "estadoAntes", "estadoDepois")
                .contains(tuple("CONCLUSAO_AUTOMATICA", "EM_CURSO", "TERMINADA"));
    }

    @Test
    void deveTerminarUmaDinamicaCaducadaERegistarCaducadaParaTerminada() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-6304", "501006304");
        UUID adminId = criarUtilizadorMaster("admin6304@dgt.pt");
        Dinamica caducada = dc.dinamica();
        caducada.setEstado(EstadoDinamica.CADUCADA);
        dinamicaRepository.saveAndFlush(caducada);

        registarExternamente(dc, dc.ordem2(), adminId);

        assertThat(dinamicaRepository.findById(caducada.getId()).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("DINAMICA", caducada.getId().toString()))
                .extracting("acao", "estadoAntes", "estadoDepois")
                .contains(tuple("CONCLUSAO_AUTOMATICA", "CADUCADA", "TERMINADA"));
    }

    @Test
    void deveConcluirDinamicaAutomaticamenteQuandoOSegundoObrigatorioEPublicado() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-1301", "501001301");
        UUID adminId = criarUtilizadorMaster("admin1301@dgt.pt");
        RegistoExternoProcessoRequest pedido1 = new RegistoExternoProcessoRequest(
                dc.ordem1().getId(), dc.entidade().getId(), "2026/DR/1301A", java.time.LocalDate.now(), "motivo", null);
        mockMvc.perform(post("/api/v1/dinamicas/" + dc.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER")).contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido1)))
                .andExpect(status().isCreated());

        Dinamica aindaEmCurso = dinamicaRepository.findById(dc.dinamica().getId()).orElseThrow();
        assertThat(aindaEmCurso.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);

        RegistoExternoProcessoRequest pedido2 = new RegistoExternoProcessoRequest(
                dc.ordem2().getId(), dc.entidade().getId(), "2026/DR/1301B", java.time.LocalDate.now(), "motivo", null);
        mockMvc.perform(post("/api/v1/dinamicas/" + dc.dinamica().getId() + "/processos/registo-externo")
                        .with(jwtComPerfilInterno(adminId, "MASTER")).contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido2)))
                .andExpect(status().isCreated());

        Dinamica concluida = dinamicaRepository.findById(dc.dinamica().getId()).orElseThrow();
        assertThat(concluida.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(concluida.getDataConclusao()).isNotNull();
        Instrumento instrumento = instrumentoRepository.findById(dc.instrumento().getId()).orElseThrow();
        assertThat(instrumento.isEmVigor()).isTrue();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("INSTRUMENTO", instrumento.getId().toString()))
                .extracting("acao").containsExactly("ATUALIZACAO_EM_VIGOR");
    }

    @Test
    void naoDeveCancelarAutomaticamenteNemCascatarLateralmenteQuandoTentativaEAnulada() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-1308", "501001308");
        Processo tentativa = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem1(), dc.autor(), dc.entidade()));
        tentativa.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(tentativa);

        mockMvc.perform(patch("/api/v1/processos/" + tentativa.getId() + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(tentativa.getId()), "motivo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));

        Dinamica aindaEmCurso = dinamicaRepository.findById(dc.dinamica().getId()).orElseThrow();
        assertThat(aindaEmCurso.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        Instrumento instrumentoInalterado = instrumentoRepository.findById(dc.instrumento().getId()).orElseThrow();
        assertThat(instrumentoInalterado.isEmVigor()).isFalse();
        assertThat(processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(dc.dinamica(), dc.ordem2()))
                .isEmpty();
    }

    @Test
    void naoDeveExistirAAcaoDeCancelarDinamica() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-1302", "501001302");

        mockMvc.perform(patch("/api/v1/dinamicas/" + dc.dinamica().getId() + "/cancelar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")).contentType("application/json")
                        .content("{\"motivo\":\"Pedido retirado pela entidade.\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(dinamicaRepository.findById(dc.dinamica().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDinamica.EM_CURSO);
    }

    // --- Consulta de Processos ---

    @Test
    void deveDevolver200AoListarProcessosComoTecnico() throws Exception {
        criarTentativaExistente("PDM-1401", "501001401");

        mockMvc.perform(get("/api/v1/processos").with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void deveFiltrarListagemDeProcessosPorEntidadeId() throws Exception {
        TentativaExistente a = criarTentativaExistente("PDM-1402", "501001402");
        criarTentativaExistente("PDM-1403", "501001403");

        mockMvc.perform(get("/api/v1/processos").param("entidadeId", a.processo().getEntidade().getId().toString())
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(a.processo().getId().toString()))
                .andExpect(jsonPath("$.content[0].entidadeId").value(a.processo().getEntidade().getId()))
                .andExpect(jsonPath("$.content[0].entidadeNif").doesNotExist())
                .andExpect(jsonPath("$.content[0].instrumentoId").value(a.instrumento().getId().toString()));
    }

    @Test
    void deveDevolverInstrumentoIdCorrespondenteAoInstrumentoDaDinamicaNaListagemDeProcessos() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1499", "501001499");

        mockMvc.perform(get("/api/v1/processos").with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].instrumentoId").value(tentativa.instrumento().getId().toString()));

        mockMvc.perform(get("/api/v1/instrumentos/" + tentativa.instrumento().getId() + "/disponivel")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk());
    }

    @Test
    void deveListarApenasProcessosDaPropriaEntidadeQuandoAtorEntidade() throws Exception {
        TentativaExistente a = criarTentativaExistente("PDM-1404", "501001404");
        criarTentativaExistente("PDM-1405", "501001405");

        mockMvc.perform(get("/api/v1/processos").with(jwtComEntidade("501001404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(a.processo().getId().toString()));
    }

    @Test
    void deveFiltrarListagemDeProcessosPorVariosEstados() throws Exception {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-1500", "501001500");
        Processo rejeitadoDgt = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem1(), dc.autor(), dc.entidade()));
        rejeitadoDgt.setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(rejeitadoDgt);
        Processo anuladoIncm = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem2(), dc.autor(), dc.entidade()));
        anuladoIncm.setEstado(EstadoProcesso.ANULADO_INCM);
        processoRepository.saveAndFlush(anuladoIncm);

        mockMvc.perform(get("/api/v1/processos").param("entidadeId", dc.entidade().getId().toString())
                        .param("estado", "REJEITADO_DGT", "ANULADO_INCM")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].estado", containsInAnyOrder("REJEITADO_DGT", "ANULADO_INCM")));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOSizeDaListagemDeProcessosExcedeOMaximo() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("size", "101")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("size"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void deveAceitarOSizeMaximoDaListagemDeProcessos() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("size", "100")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOSizeOuAPaginaDaListagemDeProcessosSaoInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("size", "0")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        mockMvc.perform(get("/api/v1/processos").param("page", "-1")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOEstadoDaListagemDeProcessosNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("estado", "XPTO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("estado"));
    }

    @Test
    void deveRecusarOsEstadosAntigosDaListagemDeProcessos() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("estado", "SUBMETIDO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOSizeDoHistoricoExcedeOMaximo() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1408", "501001408");

        mockMvc.perform(get("/api/v1/processos/" + tentativa.processo().getId() + "/historico").param("size", "101")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("size"));
    }

    @Test
    void deveFiltrarListagemDeProcessosPorDesignacaoParcialSemDistinguirMaiusculas() throws Exception {
        TentativaExistente cascais = criarTentativaExistente("CASCAIS-1600", "501001600");
        criarTentativaExistente("SINTRA-1601", "501001601");

        mockMvc.perform(get("/api/v1/processos").param("designacaoInstrumento", "cascais")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(cascais.processo().getId().toString()));
    }

    // --- Listagem: filtros por grupo, n.º de processo, ano e entidade, e ordenação ---

    private Entidade novaEntidade(String nif) {
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + nif));
    }

    private Utilizador novoUtilizadorIncm(String sufixo) {
        return utilizadorRepository.saveAndFlush(new Utilizador("Utilizador " + sufixo, "u" + sufixo + "@cm-teste.pt", "INCM"));
    }

    private Processo novoProcessoListagem(String codigo, Entidade entidade, Utilizador autor, EstadoProcesso estado) {
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios(codigo, entidade, autor);
        Processo processo = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem1(), autor, entidade));
        processo.setEstado(estado);
        return processoRepository.saveAndFlush(processo);
    }

    private RequestPostProcessor jwtExterno(Entidade entidade, Utilizador utilizador, boolean pcm) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", "Entidade Teste", "pcm", pcm)));
    }

    private RequestPostProcessor jwtExternoDoTipo(Entidade entidade, Utilizador utilizador, String tipoEntidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", tipoEntidade, "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", "Entidade Teste", "pcm", false)));
    }

    private static String[] idsDe(Processo... processos) {
        return java.util.Arrays.stream(processos).map(processo -> processo.getId().toString()).toArray(String[]::new);
    }

    @Test
    void oUtilizadorPcmRecebe404NoProcessoDeOutraEntidadeENoAtribuidoVeOEnquantoEstiverEmValidadoDgt() throws Exception {
        ProcessoValidadoDgt validado = criarProcessoValidadoDgt("PDM-B401", "501401001", "PRIMEIRA");
        Entidade pcm = novaEntidade("501401002");
        Utilizador tecnicoPcm = novoUtilizadorIncm("b401p");
        String url = "/api/v1/processos/" + validado.processo().getId();

        mockMvc.perform(get(url).with(jwtExterno(pcm, tecnicoPcm, true))).andExpect(status().isNotFound());

        Processo atribuido = processoRepository.findById(validado.processo().getId()).orElseThrow();
        atribuido.setPcmAtribuidoA(tecnicoPcm);
        processoRepository.saveAndFlush(atribuido);
        mockMvc.perform(get(url).with(jwtExterno(pcm, tecnicoPcm, true))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(pcm, tecnicoPcm, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(validado.processo()))));

        Processo enviado = processoRepository.findById(validado.processo().getId()).orElseThrow();
        enviado.setEstado(EstadoProcesso.EM_VALIDACAO_INCM);
        processoRepository.saveAndFlush(enviado);
        mockMvc.perform(get(url).with(jwtExterno(pcm, tecnicoPcm, true))).andExpect(status().isNotFound());
    }

    @Test
    void oUtilizadorPcmNaoEnviaAIncmNemNoProcessoQueLheFoiAtribuido() throws Exception {
        ProcessoValidadoDgt validado = criarProcessoValidadoDgt("PDM-B402", "501401003", "PRIMEIRA");
        Entidade pcm = novaEntidade("501401004");
        Utilizador tecnicoPcm = novoUtilizadorIncm("b402p");
        Processo atribuido = processoRepository.findById(validado.processo().getId()).orElseThrow();
        atribuido.setPcmAtribuidoA(tecnicoPcm);
        processoRepository.saveAndFlush(atribuido);

        mockMvc.perform(patch("/api/v1/processos/" + validado.processo().getId() + "/enviar-incm")
                        .with(jwtExterno(pcm, tecnicoPcm, true))
                        .contentType("application/json")
                        .content(corpoVersao(validado.processo().getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        assertThat(processoRepository.findById(validado.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.VALIDADO_DGT);
        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void oUtilizadorPcmQueNaoEAutorNemTemOProcessoAtribuidoNaoGravaOFormulario() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-B403", "501401005");
        Entidade pcm = novaEntidade("501401006");
        Utilizador tecnicoPcm = novoUtilizadorIncm("b403p");
        UUID id = tentativa.processo().getId();

        mockMvc.perform(put("/api/v1/processos/" + id + "/dados-formulario").with(jwtExterno(pcm, tecnicoPcm, true))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(id) + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void oProcedimentoVisivelNaDinamicaDaLeituraAEntidadeComProcessosNelaEMaisNinguem() throws Exception {
        Entidade ccdr = novaEntidade("501401007");
        Entidade municipio = novaEntidade("501401008");
        Entidade semProcessos = novaEntidade("501401009");
        Utilizador autorCcdr = novoUtilizadorIncm("b404a");
        Utilizador autorMunicipio = novoUtilizadorIncm("b404m");
        Utilizador outro = novoUtilizadorIncm("b404o");
        DinamicaComDoisObrigatorios dc = criarDinamicaComDoisObrigatorios("PDM-B404", ccdr, autorCcdr);
        Processo daCcdr = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem1(), autorCcdr, ccdr));
        Processo doMunicipio = processoRepository.saveAndFlush(new Processo(dc.dinamica(), dc.ordem2(), autorMunicipio, municipio));
        dc.ordem1().setVisivelNaDinamica(true);
        procedimentoConfigRepository.saveAndFlush(dc.ordem1());
        String urlDaCcdr = "/api/v1/processos/" + daCcdr.getId();

        mockMvc.perform(get(urlDaCcdr).with(jwtExterno(municipio, autorMunicipio, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis", hasSize(0)));
        mockMvc.perform(put(urlDaCcdr + "/dados-formulario").with(jwtExterno(municipio, autorMunicipio, false))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(daCcdr.getId()) + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(urlDaCcdr).with(jwtExterno(semProcessos, outro, false))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/processos/" + doMunicipio.getId()).with(jwtExterno(ccdr, autorCcdr, false)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(municipio, autorMunicipio, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(idsDe(daCcdr, doMunicipio))));
        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(semProcessos, outro, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void aSessaoDeUmaEntidadeDeTipoDgtVeEFiltraProcessosDeQualquerEntidade() throws Exception {
        Entidade dgt = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501401010", "DGT de Teste", TipoEntidade.DGT));
        Entidade outra = novaEntidade("501401011");
        Utilizador utilizadorDgt = novoUtilizadorIncm("b405d");
        Utilizador autor = novoUtilizadorIncm("b405a");
        Processo deOutra = novoProcessoListagem("L401-B405", outra, autor, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos/" + deOutra.getId()).with(jwtExternoDoTipo(dgt, utilizadorDgt, "DGT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos").param("entidadeId", outra.getId().toString())
                        .with(jwtExternoDoTipo(dgt, utilizadorDgt, "DGT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(deOutra))));
        mockMvc.perform(get("/api/v1/processos/" + deOutra.getId()).with(jwtExternoDoTipo(dgt, utilizadorDgt, "CM")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duasEntidadesComOMesmoNipcNaoVeemOsProcessosUmaDaOutra() throws Exception {
        Entidade a = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501401012", "Gabinete A"));
        Entidade b = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501401012", "Gabinete B"));
        Utilizador utilizadorA = novoUtilizadorIncm("b406a");
        Utilizador utilizadorB = novoUtilizadorIncm("b406b");
        Processo deA = novoProcessoListagem("L401-B406A", a, utilizadorA, EstadoProcesso.EM_SUBMISSAO);
        Processo deB = novoProcessoListagem("L401-B406B", b, utilizadorB, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(b, utilizadorB, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(deB))));
        mockMvc.perform(get("/api/v1/processos/" + deA.getId()).with(jwtExterno(b, utilizadorB, false)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveOrdenarAListagemPorNumeroDeProcessoDescendente() throws Exception {
        Entidade entidade = novaEntidade("501015001");
        Utilizador utilizador = novoUtilizadorIncm("150a");
        Processo p1 = novoProcessoListagem("L150-A1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo p2 = novoProcessoListagem("L150-A2", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo p3 = novoProcessoListagem("L150-A3", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(p3, p2, p1))))
                .andExpect(jsonPath("$.content[*].numeroProcesso", contains(
                        p3.getNumeroProcesso(), p2.getNumeroProcesso(), p1.getNumeroProcesso())));
    }

    @Test
    void deveOrdenarParaCorrecaoPorNumeroDeProcessoAscendente() throws Exception {
        Entidade entidade = novaEntidade("501015002");
        Utilizador utilizador = novoUtilizadorIncm("150b");
        Processo p1 = novoProcessoListagem("L150-B1", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo p2 = novoProcessoListagem("L150-B2", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo p3 = novoProcessoListagem("L150-B3", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(p1, p2, p3))));
    }

    @Test
    void deveListarEmCursoSoComOsProcessosDoProprioUtilizador() throws Exception {
        Entidade entidade = novaEntidade("501015003");
        Utilizador utilizador = novoUtilizadorIncm("150c1");
        Utilizador colega = novoUtilizadorIncm("150c2");
        Processo meuEmSubmissao = novoProcessoListagem("L150-C1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        novoProcessoListagem("L150-C2", entidade, colega, EstadoProcesso.EM_SUBMISSAO);
        Processo meuEnviado = novoProcessoListagem("L150-C3", entidade, utilizador, EstadoProcesso.ENVIADO_DGT);
        novoProcessoListagem("L150-C4", entidade, utilizador, EstadoProcesso.PUBLICADO);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_CURSO")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(meuEnviado, meuEmSubmissao))));
    }

    @Test
    void deveListarEmValidacaoDeQualquerAutorParaOUtilizadorExterno() throws Exception {
        Entidade entidade = novaEntidade("501015004");
        Utilizador utilizador = novoUtilizadorIncm("150d1");
        Utilizador colega = novoUtilizadorIncm("150d2");
        Processo emValidacaoDgt = novoProcessoListagem("L150-D1", entidade, colega, EstadoProcesso.EM_VALIDACAO_DGT);
        Processo validado = novoProcessoListagem("L150-D2", entidade, utilizador, EstadoProcesso.VALIDADO_DGT);
        Processo enviadoIncm = novoProcessoListagem("L150-D3", entidade, colega, EstadoProcesso.ENVIADO_INCM);
        Processo emValidacaoIncm = novoProcessoListagem("L150-D4", entidade, utilizador, EstadoProcesso.EM_VALIDACAO_INCM);
        novoProcessoListagem("L150-D5", entidade, utilizador, EstadoProcesso.ENVIADO_DGT);
        novoProcessoListagem("L150-D6", entidade, utilizador, EstadoProcesso.PUBLICADO);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_VALIDACAO")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        containsInAnyOrder(idsDe(emValidacaoDgt, validado, enviadoIncm, emValidacaoIncm))));
    }

    @Test
    void deveListarParaCorrecaoDoPcmComOsSeusEOsValidadosAtribuidosASi() throws Exception {
        Entidade entidade = novaEntidade("501015005");
        Utilizador utilizador = novoUtilizadorIncm("150e1");
        Utilizador colega = novoUtilizadorIncm("150e2");
        Processo meuRejeitado = novoProcessoListagem("L150-E1", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo meuAnulado = novoProcessoListagem("L150-E2", entidade, utilizador, EstadoProcesso.ANULADO_INCM);
        Processo validadoAtribuidoAMim = novoProcessoListagem("L150-E3", entidade, colega, EstadoProcesso.VALIDADO_DGT);
        validadoAtribuidoAMim.setPcmAtribuidoA(utilizador);
        processoRepository.saveAndFlush(validadoAtribuidoAMim);
        Processo validadoAtribuidoAoColega = novoProcessoListagem("L150-E4", entidade, utilizador, EstadoProcesso.VALIDADO_DGT);
        validadoAtribuidoAoColega.setPcmAtribuidoA(colega);
        processoRepository.saveAndFlush(validadoAtribuidoAoColega);
        novoProcessoListagem("L150-E5", entidade, colega, EstadoProcesso.REJEITADO_DGT);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(entidade, utilizador, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        containsInAnyOrder(idsDe(meuRejeitado, meuAnulado, validadoAtribuidoAMim))));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(idsDe(meuRejeitado, meuAnulado))));
    }

    @Test
    void deveListarEmValidacaoDoTecnicoSemEmSubmissaoEDeTodasAsEntidades() throws Exception {
        Entidade entidadeA = novaEntidade("501015006");
        Entidade entidadeB = novaEntidade("501015007");
        Utilizador utilizador = novoUtilizadorIncm("150f");
        Processo enviado = novoProcessoListagem("L150-F1", entidadeA, utilizador, EstadoProcesso.ENVIADO_DGT);
        Processo emValidacao = novoProcessoListagem("L150-F2", entidadeA, utilizador, EstadoProcesso.EM_VALIDACAO_DGT);
        Processo validado = novoProcessoListagem("L150-F3", entidadeB, utilizador, EstadoProcesso.VALIDADO_DGT);
        Processo emSubmissao = novoProcessoListagem("L150-F4", entidadeA, utilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_VALIDACAO").param("size", "100")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItems(idsDe(enviado, emValidacao, validado))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(emSubmissao.getId().toString()))));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOGrupoNaoSeAplicaAoPerfilInterno() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_CURSO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("grupo"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("grupo"));
    }

    private Instrumento instrumentoDe(Processo processo) {
        return dinamicaRepository.findById(processo.getDinamica().getId()).orElseThrow().getInstrumento();
    }

    @Test
    void deveFiltrarListagemPorConcelhoDoInstrumentoSemSairDaEntidadeDaSessao() throws Exception {
        Entidade entidade = novaEntidade("501026001");
        Entidade outraEntidade = novaEntidade("501026002");
        Utilizador utilizador = novoUtilizadorIncm("260a");
        Utilizador outroUtilizador = novoUtilizadorIncm("260b");
        Processo lisboa = novoProcessoListagem("L260-A1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo porto = novoProcessoListagem("L260-A2", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo daOutraEntidade = novoProcessoListagem("L260-A3", outraEntidade, outroUtilizador, EstadoProcesso.EM_SUBMISSAO);
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumentoDe(lisboa), "9106"));
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumentoDe(lisboa), "9110"));
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumentoDe(porto), "9312"));
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumentoDe(daOutraEntidade), "9500"));

        mockMvc.perform(get("/api/v1/processos").param("concelho", "9110").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(lisboa))));
        mockMvc.perform(get("/api/v1/processos").param("concelho", "9312").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(porto))));
        mockMvc.perform(get("/api/v1/processos").param("concelho", "9500").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/v1/processos").param("concelho", "0000").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void deveRecusarComValidacaoConcelhoENutsIIComFormatoInvalido() throws Exception {
        String[][] pedidos = {
                {"concelho", ""}, {"concelho", "11 06"}, {"concelho", "1".repeat(21)}, {"concelho", "1106;DROP"},
                {"nutsII", ""}, {"nutsII", "N".repeat(21)}, {"nutsII", "NORTE;DROP"}};
        for (String[] pedido : pedidos) {
            mockMvc.perform(get("/api/v1/processos").param(pedido[0], pedido[1])
                            .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value(pedido[0]))
                    .andExpect(jsonPath("$.campos[0].erro").value(
                            pedido[0].equals("concelho") ? "Código de concelho inválido." : "Código de NUTS II inválido."));
        }
    }

    @Test
    void deveDevolver401AoListarPorConcelhoSemSessao() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("concelho", "1106"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCombinarOGrupoComOEstadoPorIntersecao() throws Exception {
        Entidade entidade = novaEntidade("501015008");
        Utilizador utilizador = novoUtilizadorIncm("150g");
        novoProcessoListagem("L150-G1", entidade, utilizador, EstadoProcesso.EM_VALIDACAO_DGT);
        Processo validado = novoProcessoListagem("L150-G2", entidade, utilizador, EstadoProcesso.VALIDADO_DGT);

        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_VALIDACAO").param("estado", "VALIDADO_DGT")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(validado))));
    }

    @Test
    void deveFiltrarListagemPorNumeroDeProcessoERecusarValoresInvalidos() throws Exception {
        Entidade entidade = novaEntidade("501015009");
        Utilizador utilizador = novoUtilizadorIncm("150h");
        novoProcessoListagem("L150-H1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo alvo = novoProcessoListagem("L150-H2", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").param("numeroProcesso", alvo.getNumeroProcesso())
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(alvo))));
        mockMvc.perform(get("/api/v1/processos").param("numeroProcesso", "7A")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("numeroProcesso"));
        mockMvc.perform(get("/api/v1/processos").param("numeroProcesso", "123456789012345678901")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("numeroProcesso"));
    }

    @Test
    void deveFiltrarListagemPorAnoDeCriacaoERecusarAnoSemQuatroDigitos() throws Exception {
        Entidade entidade = novaEntidade("501015010");
        Utilizador utilizador = novoUtilizadorIncm("150i");
        Processo antigo = novoProcessoListagem("L150-I1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        Processo recente = novoProcessoListagem("L150-I2", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        jdbcTemplate.update("update processo set criado_em = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2025-06-15T10:00:00Z")), antigo.getId());

        mockMvc.perform(get("/api/v1/processos").param("ano", "2025").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(antigo))));
        mockMvc.perform(get("/api/v1/processos").param("ano", String.valueOf(java.time.LocalDate.now().getYear()))
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(recente))));
        mockMvc.perform(get("/api/v1/processos").param("ano", "25").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("ano"));
    }

    @Test
    void deveRespeitarOFiltroEntidadeIdSoParaPerfisInternos() throws Exception {
        Entidade entidadeA = novaEntidade("501015011");
        Entidade entidadeB = novaEntidade("501015012");
        Utilizador utilizadorA = novoUtilizadorIncm("150j1");
        Utilizador utilizadorB = novoUtilizadorIncm("150j2");
        Processo deA = novoProcessoListagem("L150-J1", entidadeA, utilizadorA, EstadoProcesso.EM_SUBMISSAO);
        Processo deB = novoProcessoListagem("L150-J2", entidadeB, utilizadorB, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").param("entidadeId", entidadeA.getId().toString())
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(deA))));
        mockMvc.perform(get("/api/v1/processos").param("entidadeId", entidadeA.getId().toString())
                        .with(jwtExterno(entidadeB, utilizadorB, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(deB))));
    }

    @Test
    void deveListarApenasProcessosDaPropriaEntidadeQuandoPcm() throws Exception {
        Entidade pcm = novaEntidade("501015013");
        Entidade outra = novaEntidade("501015014");
        Utilizador utilizador = novoUtilizadorIncm("150k1");
        Utilizador outroUtilizador = novoUtilizadorIncm("150k2");
        Processo proprio = novoProcessoListagem("L150-K1", pcm, utilizador, EstadoProcesso.EM_SUBMISSAO);
        novoProcessoListagem("L150-K2", outra, outroUtilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(pcm, utilizador, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(proprio))));
    }

    @Test
    void deveDevolverNumeroDeProcessoEIdentificadorDaEntidadeSemNifNaListagem() throws Exception {
        Entidade entidade = novaEntidade("501015015");
        Utilizador utilizador = novoUtilizadorIncm("150l");
        Processo processo = novoProcessoListagem("L150-L1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroProcesso").value(processo.getNumeroProcesso()))
                .andExpect(jsonPath("$.content[0].numeroProcesso").value(matchesPattern("\\d{5}")))
                .andExpect(jsonPath("$.content[0].entidadeId").value(entidade.getId()))
                .andExpect(jsonPath("$.content[0].entidadeNif").doesNotExist());
    }

    @Test
    void deveDevolver400ValidacaoQuandoOGrupoDaListagemNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("grupo", "INEXISTENTE")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("grupo"));
    }

    @Test
    void deveDevolver401NaListagemDeProcessosSemSessao() throws Exception {
        mockMvc.perform(get("/api/v1/processos"))
                .andExpect(status().isUnauthorized());
    }

    // --- Painel Principal (API-PROC-031) ---

    @Test
    void deveDevolverOsQuatroGruposDoUtilizadorExternoPelaOrdemDaEF() throws Exception {
        Entidade entidade = novaEntidade("501017401");
        Utilizador utilizador = novoUtilizadorIncm("174a1");
        Utilizador colega = novoUtilizadorIncm("174a2");
        novoProcessoListagem("L174-A1", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        novoProcessoListagem("L174-A2", entidade, utilizador, EstadoProcesso.EM_SUBMISSAO);
        novoProcessoListagem("L174-A3", entidade, colega, EstadoProcesso.ENVIADO_DGT);
        novoProcessoListagem("L174-A4", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        novoProcessoListagem("L174-A5", entidade, utilizador, EstadoProcesso.ANULADO_INCM);
        novoProcessoListagem("L174-A6", entidade, colega, EstadoProcesso.PUBLICADO);
        novoProcessoListagem("L174-A7", entidade, colega, EstadoProcesso.PUBLICADO);
        novoProcessoListagem("L174-A8", entidade, colega, EstadoProcesso.VALIDADO_DGT);

        mockMvc.perform(get("/api/v1/processos/painel").with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[*].grupo", contains("PUBLICADOS", "EM_CURSO", "PARA_CORRECAO", "EM_VALIDACAO")))
                .andExpect(jsonPath("$.grupos[0].contagem").value(2))
                .andExpect(jsonPath("$.grupos[1].contagem").value(2))
                .andExpect(jsonPath("$.grupos[2].contagem").value(2))
                .andExpect(jsonPath("$.grupos[3].contagem").value(1));
    }

    @Test
    void deveDevolverSoPublicadosEEmValidacaoAoPerfilInterno() throws Exception {
        Entidade entidadeA = novaEntidade("501017402");
        Entidade entidadeB = novaEntidade("501017403");
        Utilizador utilizador = novoUtilizadorIncm("174b");
        Processo enviado = novoProcessoListagem("L174-B1", entidadeA, utilizador, EstadoProcesso.ENVIADO_DGT);
        Processo emValidacao = novoProcessoListagem("L174-B2", entidadeA, utilizador, EstadoProcesso.EM_VALIDACAO_DGT);
        Processo validado = novoProcessoListagem("L174-B3", entidadeB, utilizador, EstadoProcesso.VALIDADO_DGT);
        Processo emSubmissao = novoProcessoListagem("L174-B4", entidadeA, utilizador, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos/painel").param("size", "100")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[*].grupo", contains("PUBLICADOS", "EM_VALIDACAO")))
                .andExpect(jsonPath("$.grupos[1].processos[*].id", hasItems(idsDe(enviado, emValidacao, validado))))
                .andExpect(jsonPath("$.grupos[1].processos[*].id", not(hasItem(emSubmissao.getId().toString()))));
    }

    @Test
    void deveDevolverAContagemTotalEAPaginaPorNumeroDeProcessoDescendente() throws Exception {
        Entidade entidade = novaEntidade("501017404");
        Utilizador utilizador = novoUtilizadorIncm("174c");
        List<Processo> criados = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            criados.add(novoProcessoListagem("L174-C" + i, entidade, utilizador, EstadoProcesso.PUBLICADO));
        }

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PUBLICADOS").param("size", "5")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos", hasSize(1)))
                .andExpect(jsonPath("$.grupos[0].contagem").value(8))
                .andExpect(jsonPath("$.grupos[0].processos", hasSize(5)))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(
                        criados.get(7), criados.get(6), criados.get(5), criados.get(4), criados.get(3)))));
    }

    @Test
    void deveDevolverASegundaPaginaDeUmGrupoExplicito() throws Exception {
        Entidade entidade = novaEntidade("501017405");
        Utilizador utilizador = novoUtilizadorIncm("174d");
        List<Processo> criados = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            criados.add(novoProcessoListagem("L174-D" + i, entidade, utilizador, EstadoProcesso.EM_VALIDACAO_INCM));
        }

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "EM_VALIDACAO").param("page", "1").param("size", "5")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos", hasSize(1)))
                .andExpect(jsonPath("$.grupos[0].grupo").value("EM_VALIDACAO"))
                .andExpect(jsonPath("$.grupos[0].contagem").value(7))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(criados.get(1), criados.get(0)))));
    }

    @Test
    void deveOrdenarParaCorrecaoDoPainelPorNumeroDeProcessoDescendente() throws Exception {
        Entidade entidade = novaEntidade("501017406");
        Utilizador utilizador = novoUtilizadorIncm("174e");
        Processo p1 = novoProcessoListagem("L174-E1", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo p2 = novoProcessoListagem("L174-E2", entidade, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo p3 = novoProcessoListagem("L174-E3", entidade, utilizador, EstadoProcesso.ANULADO_INCM);

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(entidade, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(p3, p2, p1))));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOGrupoNaoSeAplicaAoPerfilInternoNoPainel() throws Exception {
        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("grupo"));
        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "EM_CURSO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("grupo"));
    }

    @Test
    void deveDevolver400ValidacaoQuandoOGrupoDoPainelNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "INEXISTENTE")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("grupo"));
    }

    @Test
    void deveDevolver400ValidacaoQuandoAPaginacaoDoPainelEInvalida() throws Exception {
        for (String size : List.of("0", "101")) {
            mockMvc.perform(get("/api/v1/processos/painel").param("size", size)
                            .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("size"));
        }
        mockMvc.perform(get("/api/v1/processos/painel").param("page", "-1")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("page"));
    }

    @Test
    void deveDevolver401NoPainelSemSessao() throws Exception {
        mockMvc.perform(get("/api/v1/processos/painel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void naoDeveMostrarNoPainelProcessosDeOutraEntidade() throws Exception {
        Entidade propria = novaEntidade("501017407");
        Entidade outra = novaEntidade("501017408");
        Utilizador utilizador = novoUtilizadorIncm("174f1");
        Utilizador deOutra = novoUtilizadorIncm("174f2");
        Processo meuPublicado = novoProcessoListagem("L174-F1", propria, utilizador, EstadoProcesso.PUBLICADO);
        novoProcessoListagem("L174-F2", outra, deOutra, EstadoProcesso.PUBLICADO);
        novoProcessoListagem("L174-F3", outra, deOutra, EstadoProcesso.EM_VALIDACAO_DGT);
        novoProcessoListagem("L174-F4", outra, deOutra, EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(get("/api/v1/processos/painel").with(jwtExterno(propria, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(1))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(meuPublicado))))
                .andExpect(jsonPath("$.grupos[1].contagem").value(0))
                .andExpect(jsonPath("$.grupos[2].contagem").value(0))
                .andExpect(jsonPath("$.grupos[3].contagem").value(0));
    }

    // --- Painel Principal: «Para correção» da PCM (EF l.254, US-235) ---

    /** Utilizador PCM e a sua entidade de tipo `PCM`, como os deixa o login (vínculo à entidade). */
    private record SessaoPcm(Entidade entidade, Utilizador utilizador) {
    }

    private SessaoPcm novaSessaoPcm(String nif, String sufixo) {
        Entidade pcm = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "PCM " + nif, TipoEntidade.PCM));
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Utilizador PCM " + sufixo, "pcm" + sufixo + "@pcm.gov.pt", "INCM"));
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, pcm, Instant.now()));
        return new SessaoPcm(pcm, utilizador);
    }

    /** Token de uma sessão PCM real: `entidade.tipo_entidade = PCM` e `entidade.pcm = true` (#401). */
    private RequestPostProcessor jwtPcm(SessaoPcm sessao) {
        return jwt().jwt(builder -> builder.claim(SUB, sessao.utilizador().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "PCM", "id", sessao.entidade().getId(),
                        "nif", sessao.entidade().getNipc(), "nome", "PCM", "pcm", true)));
    }

    /** Processo de 1.ª série `VALIDADO_DGT` de uma Câmara, atribuído a `atribuidoA` (nulo: nunca enviado à PCM). */
    private Processo validadoDgtPrimeiraSerie(String codigo, String nif, Utilizador atribuidoA) {
        Processo processo = criarProcessoValidadoDgt(codigo, nif, "PRIMEIRA").processo();
        processo.setPcmAtribuidoA(atribuidoA);
        return processoRepository.saveAndFlush(processo);
    }

    private Processo comEstado(Processo processo, EstadoProcesso estado) {
        Processo atual = processoRepository.findById(processo.getId()).orElseThrow();
        atual.setEstado(estado);
        return processoRepository.saveAndFlush(atual);
    }

    /**
     * Os dois `VALIDADO_DGT` atribuídos ao utilizador da sessão, um atribuído a outro utilizador PCM (da mesma
     * entidade PCM) e um de 1.ª série que nunca foi à PCM.
     */
    private Processo[] cenarioDoisAtribuidos(SessaoPcm sessao, String prefixo, String nifBase) {
        SessaoPcm colega = novaSessaoPcm(nifBase + "9", prefixo.toLowerCase() + "c");
        Processo primeiro = validadoDgtPrimeiraSerie(prefixo + "1", nifBase + "1", sessao.utilizador());
        Processo segundo = validadoDgtPrimeiraSerie(prefixo + "2", nifBase + "2", sessao.utilizador());
        validadoDgtPrimeiraSerie(prefixo + "3", nifBase + "3", colega.utilizador());
        validadoDgtPrimeiraSerie(prefixo + "4", nifBase + "4", null);
        return new Processo[] {primeiro, segundo};
    }

    @Test
    void oPainelDaPcmEmParaCorrecaoTemSoOsDoisValidadosDgtAtribuidosAoUtilizador() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("501235010", "235a");
        Processo[] meus = cenarioDoisAtribuidos(sessao, "PDM-B235A", "50123501");

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos", hasSize(1)))
                .andExpect(jsonPath("$.grupos[0].grupo").value("PARA_CORRECAO"))
                .andExpect(jsonPath("$.grupos[0].contagem").value(2))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", containsInAnyOrder(idsDe(meus))))
                .andExpect(jsonPath("$.grupos[0].processos[*].estado", everyItem(is("VALIDADO_DGT"))));
    }

    @Test
    void aListagemDaPcmEmParaCorrecaoDevolveOsMesmosDoisPaginadosPorPageESize() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("501235020", "235b");
        Processo[] meus = cenarioDoisAtribuidos(sessao, "PDM-B235B", "50123502");
        // `GET /processos?grupo=PARA_CORRECAO` ordena por n.º de processo ascendente: o primeiro criado vem primeiro.
        String primeiro = meus[0].getId().toString();
        String segundo = meus[1].getId().toString();

        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(primeiro, segundo)));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").param("page", "0").param("size", "1")
                        .with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(primeiro)));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").param("page", "1").param("size", "1")
                        .with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(segundo)));
    }

    @Test
    void oProcessoAtribuidoQuePassouAEnviadoIncmOuAAnuladoSaiDoParaCorrecaoDaPcm() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("501235030", "235c");
        Processo continua = validadoDgtPrimeiraSerie("PDM-B235C1", "501235031", sessao.utilizador());
        Processo enviado = comEstado(validadoDgtPrimeiraSerie("PDM-B235C2", "501235032", sessao.utilizador()),
                EstadoProcesso.ENVIADO_INCM);
        Processo anulado = comEstado(validadoDgtPrimeiraSerie("PDM-B235C3", "501235033", sessao.utilizador()),
                EstadoProcesso.ANULADO);
        assertThat(processoRepository.findById(enviado.getId()).orElseThrow().getPcmAtribuidoA()).isNotNull();
        assertThat(processoRepository.findById(anulado.getId()).orElseThrow().getPcmAtribuidoA()).isNotNull();

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(1))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(continua))));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(continua))));
    }

    @Test
    void oProcessoCujaAtribuicaoFoiLimpaPelaDevolucaoADgtSaiDoParaCorrecaoDaPcm() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("501235040", "235d");
        Processo continua = validadoDgtPrimeiraSerie("PDM-B235D1", "501235041", sessao.utilizador());
        Processo devolvido = validadoDgtPrimeiraSerie("PDM-B235D2", "501235042", sessao.utilizador());
        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(2));

        // A devolução à DGT (TR-114) limpa `pcm_atribuido_a`, grava `pcm_devolvido_em` e o estado continua `VALIDADO_DGT`.
        Processo limpo = processoRepository.findById(devolvido.getId()).orElseThrow();
        limpo.setPcmAtribuidoA(null);
        limpo.setPcmDevolvidoEm(Instant.now());
        processoRepository.saveAndFlush(limpo);
        assertThat(processoRepository.findById(devolvido.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.VALIDADO_DGT);

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(1))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(continua))));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(continua))));
    }

    @Test
    void oUtilizadorDaCamaraSoRecebeOSeuRejeitadoDgtEmParaCorrecaoSemOsValidadosDgt() throws Exception {
        Entidade camara = novaEntidade("501235050");
        Utilizador utilizador = novoUtilizadorIncm("235e1");
        SessaoPcm pcm = novaSessaoPcm("501235059", "235e2");
        Processo rejeitado = novoProcessoListagem("L235-E1", camara, utilizador, EstadoProcesso.REJEITADO_DGT);
        Processo validadoDaEntidade = novoProcessoListagem("L235-E2", camara, utilizador, EstadoProcesso.VALIDADO_DGT);
        validadoDaEntidade.setPcmAtribuidoA(pcm.utilizador());
        processoRepository.saveAndFlush(validadoDaEntidade);
        validadoDgtPrimeiraSerie("PDM-B235E3", "501235053", pcm.utilizador());

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(camara, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(1))
                .andExpect(jsonPath("$.grupos[0].processos[*].id", contains(idsDe(rejeitado))));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO")
                        .with(jwtExterno(camara, utilizador, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(rejeitado))));
    }

    @Test
    void oMasterEOTecnicoRecebem400ValidacaoNoCampoGrupoComParaCorrecaoNoPainel() throws Exception {
        for (String perfil : List.of("MASTER", "TECNICO")) {
            mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "PARA_CORRECAO")
                            .with(jwtComPerfilInterno(UUID.randomUUID(), perfil)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("grupo"));
        }
    }

    @Test
    void aContagemDeParaCorrecaoNoPainelDaPcmSemGrupoEIgualAoTotalDaListagem() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("501235060", "235f");
        cenarioDoisAtribuidos(sessao, "PDM-B235F", "50123506");

        String painel = mockMvc.perform(get("/api/v1/processos/painel").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[*].grupo", hasItem("PARA_CORRECAO")))
                .andReturn().getResponse().getContentAsString();
        String listagem = mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long contagem = 0;
        for (var grupo : objectMapper.readTree(painel).get("grupos")) {
            if ("PARA_CORRECAO".equals(grupo.get("grupo").asText())) {
                contagem = grupo.get("contagem").asLong();
            }
        }
        assertThat(contagem).isEqualTo(2L).isEqualTo(objectMapper.readTree(listagem).get("totalElements").asLong());
    }

    @Test
    void deveDevolverHistoricoComTresTransicoesMaisRecentePrimeiro() throws Exception {
        TentativaExistente tentativa = criarTentativaExistente("PDM-1406", "501001406");
        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501001406", tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(PedidosSubmissao.confirmado(versao(tentativa.processo().getId()),
                                "Resumo", java.time.LocalDate.now(), "Norma", "TEXTO_INTEGRAL", null, null))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/resgatar")
                        .with(jwtComEntidadeEUtilizadorReal("501001406", tentativa.autorId()))
                        .contentType("application/json")
                        .content(corpoVersao(tentativa.processo().getId())))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/processos/" + tentativa.processo().getId() + "/submeter")
                        .with(jwtComEntidadeEUtilizadorReal("501001406", tentativa.autorId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(PedidosSubmissao.confirmado(versao(tentativa.processo().getId()),
                                "Resumo", java.time.LocalDate.now(), "Norma", "TEXTO_INTEGRAL", null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + tentativa.processo().getId() + "/historico")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].acao").value("SUBMETER"))
                .andExpect(jsonPath("$.content[1].acao").value("RESGATAR"))
                .andExpect(jsonPath("$.content[2].acao").value("SUBMETER"));
    }

    @Test
    void deveDevolver403SemPermissaoParaOTipoAoCriarInstrumentoDeEntidadeCcdrDr() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T01", TipoEntidade.CM);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004001", "Câmara T01"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t01@cm-t01.pt");
        CriarInstrumentoRequest pedido = new CriarInstrumentoRequest(cadeia.tipoInstrumento().getId(), "PDM T01", SINTRA);

        mockMvc.perform(post(URL).with(jwtComTipoEntidade("501004001", utilizadorId, "CCDR_DR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão para o tipo de Instrumento."));

        mockMvc.perform(post(URL).with(jwtComTipoEntidade("501004001", utilizadorId, "CM"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isCreated());
    }

    @Test
    void deveDevolver403AoCriarInstrumentoIniciarDinamicaECriarProcessoQuandoASessaoNaoTemTipoDeEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T02", TipoEntidade.CM);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004002", "Câmara T02"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T02", "instrumento-t02@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t02@cm-t02.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T02", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Em curso", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-T02", "Ato T02", 1);
        tipoAto.setIncmActTypeIdSerie2(4002L);
        tipoAtoRepository.saveAndFlush(tipoAto);

        mockMvc.perform(post(URL).with(jwtComTipoEntidade("501004002", utilizadorId, null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(cadeia.tipoInstrumento().getId(), "PDM T02", SINTRA))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComTipoEntidade("501004002", utilizadorId, null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica."));

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComTipoEntidade("501004002", utilizadorId, null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão neste procedimento."));
    }

    @Test
    void deveDevolver403APcmAoCriarInstrumentoIniciarDinamicaECriarProcessoMesmoComALinhaPcmSemCriarNada() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PNPOT-T237", TipoEntidade.PCM);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T237", "instrumento-t237@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t237@pcm-t237.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PNPOT/T237", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Em curso", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-T237", "Ato T237", 1);
        tipoAto.setIncmActTypeIdSerie2(4237L);
        tipoAtoRepository.saveAndFlush(tipoAto);
        long instrumentos = instrumentoRepository.count();
        long dinamicas = dinamicaRepository.count();
        long processos = processoRepository.count();
        String mensagem = "A PCM não cria Instrumentos, Dinâmicas nem processos.";

        mockMvc.perform(post(URL).with(jwtComTipoEntidade("600000237", utilizadorId, "PCM"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(cadeia.tipoInstrumento().getId(), "PNPOT T237", SINTRA))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value(mensagem));

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComTipoEntidade("600000237", utilizadorId, "PCM"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value(mensagem));

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComTipoEntidade("600000237", utilizadorId, "PCM"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value(mensagem));

        assertThat(instrumentoRepository.count()).isEqualTo(instrumentos);
        assertThat(dinamicaRepository.count()).isEqualTo(dinamicas);
        assertThat(processoRepository.count()).isEqualTo(processos);

        mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(jwtComTipoEntidade("600000237", utilizadorId, "PCM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEntidade").value("PCM"))
                .andExpect(jsonPath("$.procedimentosPermitidos", hasSize(0)));
    }

    @Test
    void deveDevolver403AoMasterECriarInstrumentoEIniciarDinamicaMesmoComLinhaDgt() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T03", TipoEntidade.DGT);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004003", "Câmara T03"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T03", "instrumento-t03@dgt.pt", "INCM"));
        UUID masterId = criarUtilizadorMaster("master-t03@dgt.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T03", criadoPor, criadoPor));

        mockMvc.perform(post(URL).with(jwtComPerfilInterno(masterId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(cadeia.tipoInstrumento().getId(), "PDM T03", SINTRA))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComPerfilInterno(masterId, "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver403AoTecnicoECriarInstrumentoEIniciarDinamica() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T04", TipoEntidade.DGT);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004004", "Câmara T04"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T04", "instrumento-t04@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T04", criadoPor, criadoPor));

        mockMvc.perform(post(URL).with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarInstrumentoRequest(cadeia.tipoInstrumento().getId(), "PDM T04", SINTRA))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveDevolver201AoCriarProcessoQuandoAEntidadeETipoDgtETemLinhaDgt() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T05", TipoEntidade.DGT);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004005", "Câmara T05"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T05", "instrumento-t05@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t05@dgt-t05.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T05", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Em curso", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-T05", "Ato T05", 1);
        tipoAto.setIncmActTypeIdSerie2(4005L);
        tipoAtoRepository.saveAndFlush(tipoAto);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComTipoEntidade("501004005", utilizadorId, "DGT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId()))))
                .andExpect(status().isCreated());
    }

    @Test
    void deveDevolver403AoCriarProcessoQuandoOTipoDaEntidadeNaoTemLinhaNoProcedimento() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T06", TipoEntidade.CM);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004006", "Câmara T06"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T06", "instrumento-t06@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t06@se-t06.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T06", criadoPor, criadoPor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, cadeia.dinamicaConfig(), "Em curso", criadoPor));
        TipoAto tipoAto = new TipoAto("ATO-T06", "Ato T06", 1);
        tipoAto.setIncmActTypeIdSerie2(4006L);
        tipoAtoRepository.saveAndFlush(tipoAto);

        mockMvc.perform(post("/api/v1/dinamicas/" + dinamica.getId() + "/processos").with(jwtComTipoEntidade("501004006", utilizadorId, "SEC_ESTADO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarProcessoRequest(cadeia.procedimentoConfig().getId(), tipoAto.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Tipo da entidade da sessão sem permissão neste procedimento."));
    }

    @Test
    void deveDevolver403AoIniciarDinamicaQuandoALinhaDoTipoEstaDesativada() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T07", TipoEntidade.CM);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004007", "Câmara T07"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T07", "instrumento-t07@dgt.pt", "INCM"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-t07@cm-t07.pt");
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T07", criadoPor, criadoPor));
        String corpo = objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"));

        PermissaoAcao linha = permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM).stream()
                .filter(permissao -> permissao.getInstrumentoProcedimentoConfig().getId().equals(cadeia.procedimentoConfig().getId()))
                .findFirst().orElseThrow();
        linha.setAtivo(false);
        permissaoAcaoRepository.saveAndFlush(linha);

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComTipoEntidade("501004007", utilizadorId, "CM"))
                        .contentType("application/json").content(corpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        linha.setAtivo(true);
        permissaoAcaoRepository.saveAndFlush(linha);

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComTipoEntidade("501004007", utilizadorId, "CM"))
                        .contentType("application/json").content(corpo))
                .andExpect(status().isCreated());
    }

    @Test
    void deveDevolver403AoIniciarDinamicaQuandoASessaoNaoTemTipoDeEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T08", TipoEntidade.CM);
        Entidade entidadeDona = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004008", "Câmara Dona T08"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T08", "instrumento-t08@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T08", criadoPor, criadoPor));

        mockMvc.perform(post(URL + "/" + instrumento.getId() + "/dinamicas").with(jwtComTipoEntidade("501004009", criadoPor.getId(), null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CriarDinamicaRequest(cadeia.dinamicaConfig().getId(), "Alteração"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void disponivelSoDeveListarAsDinamicasEOsProcedimentosComLinhaAtivaDoTipoDaEntidade() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaTipo("PDM-T09", TipoEntidade.CM);
        TipoDinamica outraTipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-PDM-T09", "Alteração"));
        InstrumentoDinamicaConfig outraDinamica = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(cadeia.tipoInstrumento(), outraTipoDinamica));
        InstrumentoProcedimentoConfig outroProcedimento = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                outraDinamica, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-OUTRO-T09", "Outro T09")), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.SEC_ESTADO, outroProcedimento));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501004010", "Câmara T09"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Instrumento T09", "instrumento-t09@dgt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(cadeia.tipoInstrumento(), "1", "2026/PDM/T09", criadoPor, criadoPor));
        String url = URL + "/" + instrumento.getId() + "/disponivel";

        mockMvc.perform(get(url).with(jwtComTipoEntidade("501004010", UUID.randomUUID(), "CM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasDisponiveis[0].tipoDinamica").value("ELAB-PDM-T09"));

        mockMvc.perform(get(url).with(jwtComTipoEntidade("501004010", UUID.randomUUID(), "SEC_ESTADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasDisponiveis[0].tipoDinamica").value("ALT-PDM-T09"));

        mockMvc.perform(get(url).with(jwtComTipoEntidade("501004010", UUID.randomUUID(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis", hasSize(0)));
    }

    private CriarInstrumentoRequest pedidoDoTipo(TipoInstrumento tipo, String designacao, List<String> concelhos) {
        return new CriarInstrumentoRequest(tipo.getId(), designacao, concelhos);
    }

    @Test
    void deveDevolver400AoCriarInstrumentoComUmConcelhoRepetido() throws Exception {
        TipoInstrumento tipoInstrumento = criarTipoInstrumentoComPermissaoParaEntidade("PDIM-REP");
        tipoInstrumento.setAmbitoTerritorial(AmbitoTerritorial.INTERMUNICIPAL);
        tipoInstrumentoRepository.saveAndFlush(tipoInstrumento);
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000451", "Câmara REP"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-rep@cm-rep.pt");

        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000451", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedidoDoTipo(tipoInstrumento, "PDIM REP", List.of("1111", "1111")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("concelhos"));
        assertThat(instrumentoRepository.findAll()).noneMatch(i -> "PDIM REP".equals(i.getDesignacao()));
    }

    @Test
    void deveDevolver400ComVariosConcelhosNoTipoMunicipalEComUmSoNoIntermunicipal() throws Exception {
        TipoInstrumento municipal = criarTipoInstrumentoComPermissaoParaEntidade("PDM-AMB");
        municipal.setAmbitoTerritorial(AmbitoTerritorial.MUNICIPAL);
        tipoInstrumentoRepository.saveAndFlush(municipal);
        TipoInstrumento intermunicipal = criarTipoInstrumentoComPermissaoParaEntidade("PDIM-AMB");
        intermunicipal.setAmbitoTerritorial(AmbitoTerritorial.INTERMUNICIPAL);
        tipoInstrumentoRepository.saveAndFlush(intermunicipal);
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000452", "Câmara AMB"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-amb@cm-amb.pt");

        for (CriarInstrumentoRequest pedido : List.of(pedidoDoTipo(municipal, "PDM AMB", List.of("1111", "1105")),
                pedidoDoTipo(intermunicipal, "PDIM AMB", SINTRA))) {
            mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000452", utilizadorId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(pedido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("concelhos"));
        }
        mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000452", utilizadorId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(pedidoDoTipo(intermunicipal, "PDIM AMB", List.of("1105", "1111")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.concelhos", contains("1105", "1111")));
    }

    @Test
    void deveDevolverOAmbitoTerritorialNosTiposDeInstrumento() throws Exception {
        CadeiaConfigurada cadeia = criarCadeiaComPermissaoParaEntidade("PDM-LST-AMB");
        cadeia.tipoInstrumento().setAmbitoTerritorial(AmbitoTerritorial.MUNICIPAL);
        tipoInstrumentoRepository.saveAndFlush(cadeia.tipoInstrumento());
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000453", "Câmara LSA"));
        UUID utilizadorId = criarUtilizadorIncm("utilizador-lsa@cm-lsa.pt");

        mockMvc.perform(get("/api/v1/tipos-instrumento").with(jwtComEntidadeEUtilizadorReal("501000453", utilizadorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'PDM-LST-AMB')].ambitoTerritorial").value(contains("MUNICIPAL")));
    }

    @Test
    void deveMostrarOsConcelhosNoPainelDoInstrumento() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL-CON", "509876199");
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(painel.instrumento(), "1105"));
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(painel.instrumento(), "1111"));

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876199")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concelhos", contains("1105", "1111")));
    }

    /** Códigos fora dos de teste: a réplica com concelhos muda a validação dos concelhos na criação. */
    private List<SnitReferencia> concelhosNaReplica(String... dtccENome) {
        List<SnitReferencia> linhas = new ArrayList<>();
        for (int i = 0; i < dtccENome.length; i += 2) {
            linhas.add(snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES,
                    dtccENome[i], dtccENome[i + 1], "{\"dtcc\":\"" + dtccENome[i] + "\"}", Instant.now())));
        }
        return linhas;
    }

    @Test
    void aListagemTrazOsNomesDosConcelhosDaReplicaPelaOrdemDosConcelhosEOmiteOsSemConcelhos() throws Exception {
        TipoInstrumento tipo = criarTipoInstrumentoComPermissaoParaEntidade("PDIM-NOM");
        entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000582", "CCDR NOM"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Utilizador NOM", "utilizador-nom@cm.pt", "INCM"));
        Instrumento intermunicipal = instrumentoRepository.saveAndFlush(
                new Instrumento(tipo, "NOM-1", "PIOT NOM", criadoPor, criadoPor));
        Instrumento semConcelhos = instrumentoRepository.saveAndFlush(
                new Instrumento(tipo, "NOM-2", "Plano sem concelhos NOM", criadoPor, criadoPor));
        instrumentoConcelhoRepository.saveAllAndFlush(List.of(new InstrumentoConcelho(intermunicipal, "9583"),
                new InstrumentoConcelho(intermunicipal, "9581"), new InstrumentoConcelho(intermunicipal, "9582")));
        List<SnitReferencia> replica = concelhosNaReplica("9581", "CONCELHO NOM A", "9583", "CONCELHO NOM C");
        try {
            mockMvc.perform(get(URL).with(jwtComEntidade("501000582")).param("tipoInstrumentoId", tipo.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[?(@.designacao == 'PIOT NOM')].concelhos[*]")
                            .value(contains("9581", "9582", "9583")))
                    .andExpect(jsonPath("$.content[?(@.designacao == 'PIOT NOM')].nomesConcelhos[*]")
                            .value(contains("CONCELHO NOM A", "9582", "CONCELHO NOM C")))
                    .andExpect(jsonPath("$.content[?(@.designacao == 'Plano sem concelhos NOM')].concelhos[*]").isEmpty())
                    .andExpect(jsonPath("$.content[?(@.id == '" + semConcelhos.getId() + "')].nomesConcelhos").isEmpty());
        } finally {
            snitReferenciaRepository.deleteAll(replica);
        }
    }

    @Test
    void oPainelEOPostTrazemOsNomesDosConcelhosDaReplica() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL-NOM", "509876582");
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(painel.instrumento(), "9584"));
        List<SnitReferencia> replica = concelhosNaReplica("9584", "CONCELHO NOM D");
        try {
            mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876582")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nomesConcelhos", contains("CONCELHO NOM D")));

            TipoInstrumento tipo = criarTipoInstrumentoComPermissaoParaEntidade("PDM-POST-NOM");
            entidadeRepository.saveAndFlush(EntidadeTeste.nova("501000583", "Câmara POST NOM"));
            UUID utilizadorId = criarUtilizadorIncm("utilizador-post-nom@cm.pt");
            mockMvc.perform(post(URL).with(jwtComEntidadeEUtilizadorReal("501000583", utilizadorId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(pedidoDoTipo(tipo, "PDM POST NOM", List.of("9584")))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nomesConcelhos", contains("CONCELHO NOM D")));
        } finally {
            snitReferenciaRepository.deleteAll(replica);
        }
    }

    @Test
    void oPainelDeUmInstrumentoSemConcelhosNaoTrazNomesConcelhos() throws Exception {
        PainelConfigurado painel = criarPainel("PDM-PAINEL-SEMNOM", "509876583");

        mockMvc.perform(get(URL + "/" + painel.instrumento().getId()).with(jwtComEntidade("509876583")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concelhos", hasSize(0)))
                .andExpect(jsonPath("$.nomesConcelhos").doesNotExist());
    }

    // --- Devolução do processo à DGT pela PCM (API-PCM-003, TR-114) ---

    /** Processo de 1.ª série `VALIDADO_DGT` atribuído à sessão PCM, com a linha `permissao_acao` do tipo `PCM`. */
    private Processo atribuidoComLinhaPcm(String codigo, String nif, SessaoPcm sessao) {
        Processo processo = validadoDgtPrimeiraSerie(codigo, nif, sessao.utilizador());
        // Um plano submetido traz os elementos afetados; sem eles a estrutura documental não tem itens aplicáveis.
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo = processoRepository.saveAndFlush(processo);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.PCM, processo.getInstrumentoProcedimentoConfig()));
        return processo;
    }

    /** Item documental obrigatório do texto final da RCM; com `anexado`, já com o ficheiro carregado. */
    private DocumentoConfig textoRcm(Processo processo, boolean anexado) {
        DocumentoConfig item = new DocumentoConfig(processo.getInstrumentoProcedimentoConfig(), "TEXTO_RCM",
                "Texto final da RCM", "Peças Escritas", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        item.setObrigatorio(true);
        item.setIndependenteElementosAfetados(true);
        item = documentoConfigRepository.saveAndFlush(item);
        if (anexado) {
            ProcessoDocumento documento = new ProcessoDocumento(processo, item, CategoriaDocumento.PUBLICACAO, "pdf",
                    "texto-rcm.pdf", "privado/p/" + UUID.randomUUID(), "b".repeat(64), "v1", 10L, processo.getCriadoPor());
            documento.setEstadoValidacao(EstadoValidacao.VALIDADO);
            processoDocumentoRepository.saveAndFlush(documento);
        }
        return item;
    }

    private org.springframework.test.web.servlet.ResultActions devolverDgt(UUID processoId, long versao, String observacoes,
                                                                        RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processoId + "/devolver-dgt").with(jwt)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new DevolverDgtRequest(versao, observacoes))));
    }

    private List<Auditoria> registosDevolverDgt(UUID processoId) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()).stream()
                .filter(linha -> "DEVOLVER_DGT".equals(linha.getAcao())).toList();
    }

    /** O pedido recusado não grava nada: a mesma `versao`, estado e atribuição, sem data de devolução nem auditoria. */
    private void semDevolucao(Processo antes) {
        Processo depois = processoRepository.findById(antes.getId()).orElseThrow();
        assertThat(depois.getVersao()).isEqualTo(antes.getVersao());
        assertThat(depois.getEstado()).isEqualTo(antes.getEstado());
        assertThat(depois.getPcmAtribuidoA() == null ? null : depois.getPcmAtribuidoA().getId())
                .isEqualTo(antes.getPcmAtribuidoA() == null ? null : antes.getPcmAtribuidoA().getId());
        assertThat(depois.getPcmDevolvidoEm()).isEqualTo(antes.getPcmDevolvidoEm());
        assertThat(registosDevolverDgt(antes.getId())).hasSize(antes.getPcmDevolvidoEm() == null ? 0 : 1);
    }

    @Test
    void aPcmDevolveADgtOProcessoAtribuidoComOTextoDaRcmMantendoOEstadoLimpandoAAtribuicaoEAuditando() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410010", "341a");
        Processo processo = atribuidoComLinhaPcm("PDM-B341A", "503410011", sessao);
        textoRcm(processo, true);
        UUID id = processo.getId();
        long versaoAntes = versao(id);
        String observacoes = "Texto final da RCM anexado; campos confirmados.";
        Instant antes = Instant.now().minusSeconds(1);

        devolverDgt(id, versaoAntes, "  " + observacoes + "  ", jwtPcm(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"))
                .andExpect(jsonPath("$.pcmAtribuidoAId").doesNotExist())
                .andExpect(jsonPath("$.pcmDevolvidoEm").isNotEmpty())
                .andExpect(jsonPath("$.versao").value(versaoAntes + 1));

        Processo depois = processoRepository.findById(id).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(depois.getPcmAtribuidoA()).isNull();
        assertThat(depois.getPcmDevolvidoEm()).isBetween(antes, Instant.now().plusSeconds(1));
        List<Auditoria> registos = registosDevolverDgt(id);
        assertThat(registos).hasSize(1);
        assertThat(registos.get(0).getUtilizadorId()).isEqualTo(sessao.utilizador().getId());
        var dados = objectMapper.readTree(registos.get(0).getDadosAlteracao());
        assertThat(dados.get("pcmAtribuidoAId").asText()).isEqualTo(sessao.utilizador().getId().toString());
        assertThat(dados.get("observacoes").asText()).isEqualTo(observacoes);
    }

    @Test
    void osLogsDaDevolucaoTemOProcessoEOUtilizadorENuncaAsObservacoes() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410020", "341b");
        Processo processo = atribuidoComLinhaPcm("PDM-B341B", "503410021", sessao);
        String observacoes = "Observação reservada da PCM 341.";
        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        raiz.addAppender(logs);
        try {
            devolverDgt(processo.getId(), versao(processo.getId()), observacoes, jwtPcm(sessao)).andExpect(status().isOk());
        } finally {
            raiz.detachAppender(logs);
        }

        List<String> mensagens = logs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensagens).anySatisfy(mensagem -> assertThat(mensagem)
                .contains("Processo devolvido à DGT pela PCM", processo.getId().toString(), sessao.utilizador().getId().toString()));
        assertThat(mensagens).noneMatch(mensagem -> mensagem.contains(observacoes));
    }

    @Test
    void oProcessoJaDevolvidoDa404NumaSegundaDevolucaoENadaMuda() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410030", "341c");
        Processo processo = atribuidoComLinhaPcm("PDM-B341C", "503410031", sessao);
        UUID id = processo.getId();
        devolverDgt(id, versao(id), null, jwtPcm(sessao)).andExpect(status().isOk());
        Processo devolvido = processoRepository.findById(id).orElseThrow();

        devolverDgt(id, versao(id), null, jwtPcm(sessao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));

        semDevolucao(devolvido);
    }

    @Test
    void oProcessoAtribuidoAOutroUtilizadorPcmOuNuncaEnviadoDa404() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410040", "341d");
        SessaoPcm colega = novaSessaoPcm("503410049", "341dc");
        Processo doColega = atribuidoComLinhaPcm("PDM-B341D1", "503410041", colega);
        Processo nuncaEnviado = validadoDgtPrimeiraSerie("PDM-B341D2", "503410042", null);

        for (Processo processo : List.of(doColega, nuncaEnviado)) {
            Processo antes = processoRepository.findById(processo.getId()).orElseThrow();
            devolverDgt(processo.getId(), antes.getVersao(), null, jwtPcm(sessao))
                    .andExpect(status().isNotFound());
            semDevolucao(antes);
        }
        assertThat(processoRepository.findById(doColega.getId()).orElseThrow().getPcmAtribuidoA().getId())
                .isEqualTo(colega.utilizador().getId());
    }

    @Test
    void oTecnicoOMasterEOUtilizadorDaCamaraRecebem403PcmAtorNaoAutorizadoENadaMuda() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410050", "341e");
        Processo processo = atribuidoComLinhaPcm("PDM-B341E", "503410051", sessao);
        Utilizador daCamara = novoUtilizadorIncm("341e1");
        Entidade camara = entidadeRepository.findAllByNipc("503410051").get(0);
        Processo antes = processoRepository.findById(processo.getId()).orElseThrow();

        for (RequestPostProcessor jwt : List.of(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"),
                jwtComPerfilInterno(UUID.randomUUID(), "MASTER"), jwtExternoDoTipo(camara, daCamara, "CM"))) {
            devolverDgt(processo.getId(), antes.getVersao(), "Tentativa.", jwt)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PCM_ATOR_NAO_AUTORIZADO"));
        }

        semDevolucao(antes);
    }

    @Test
    void semALinhaPcmNaConfiguracaoDoProcedimentoAPcmRecebe403PcmAtorNaoAutorizado() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410060", "341f");
        Processo processo = validadoDgtPrimeiraSerie("PDM-B341F", "503410061", sessao.utilizador());

        devolverDgt(processo.getId(), versao(processo.getId()), null, jwtPcm(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PCM_ATOR_NAO_AUTORIZADO"));

        semDevolucao(processo);
    }

    @Test
    void oProcessoAtribuidoEAnuladoPeloMasterDa409PcmEstadoInvalido() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410070", "341g");
        Processo processo = atribuidoComLinhaPcm("PDM-B341G", "503410071", sessao);
        UUID id = processo.getId();
        mockMvc.perform(patch("/api/v1/processos/" + id + "/anular")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AnularProcessoRequest(versao(id), "Pedido da entidade."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));
        Processo anulado = processoRepository.findById(id).orElseThrow();
        assertThat(anulado.getPcmAtribuidoA().getId()).isEqualTo(sessao.utilizador().getId());

        devolverDgt(id, anulado.getVersao(), null, jwtPcm(sessao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Só processos validados pela DGT podem ser devolvidos."));

        semDevolucao(anulado);
    }

    @Test
    void semOTextoDaRcmAPcmRecebe409PcmDocumentosObrigatoriosEmFaltaComOItemEmCampos() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410080", "341h");
        Processo processo = atribuidoComLinhaPcm("PDM-B341H", "503410081", sessao);
        textoRcm(processo, false);
        Processo antes = processoRepository.findById(processo.getId()).orElseThrow();

        devolverDgt(processo.getId(), antes.getVersao(), null, jwtPcm(sessao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.mensagem").value("Falta anexar documentos obrigatórios."))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("documentos.TEXTO_RCM"));

        semDevolucao(antes);
    }

    @Test
    void comUmaVersaoAntigaAPcmRecebe409VersaoDesatualizada() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410090", "341i");
        Processo antes = processoRepository.findById(atribuidoComLinhaPcm("PDM-B341I", "503410091", sessao).getId()).orElseThrow();

        devolverDgt(antes.getId(), antes.getVersao() + 1, null, jwtPcm(sessao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        semDevolucao(antes);
    }

    @Test
    void comOProcessoComAPcmOEnvioAIncmDa409PcmJaAtribuidoESemEnviarIncmNasAcoes() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410100", "341j");
        Processo processo = atribuidoComLinhaPcm("PDM-B341J", "503410101", sessao);
        UUID id = processo.getId();
        UUID tecnicoId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm")
                        .with(jwtComPerfilInterno(tecnicoId, "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_JA_ATRIBUIDO"))
                .andExpect(jsonPath("$.mensagem").value("O processo está com a PCM."));

        assertThat(processoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        verify(envioIncmService, never()).enviar(any(), any());
        mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("enviar-incm"))))
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("devolver-dgt"))));
    }

    @Test
    void depoisDaDevolucaoOuSemPassarPelaPcmOTecnicoEnviaAIncm() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410110", "341k");
        Processo devolvido = atribuidoComLinhaPcm("PDM-B341K1", "503410111", sessao);
        Processo nuncaEnviado = validadoDgtPrimeiraSerie("PDM-B341K2", "503410112", null);
        devolverDgt(devolvido.getId(), versao(devolvido.getId()), null, jwtPcm(sessao)).andExpect(status().isOk());
        UUID tecnicoId = UUID.randomUUID();

        for (Processo processo : List.of(devolvido, nuncaEnviado)) {
            UUID id = processo.getId();
            mockMvc.perform(get("/api/v1/processos/" + id).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.acoesDisponiveis", hasItem("enviar-incm")));
            envioLevaPara(id, EstadoProcesso.EM_VALIDACAO_INCM);
            mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm")
                            .with(jwtComPerfilInterno(tecnicoId, "TECNICO"))
                            .contentType("application/json")
                            .content(corpoVersao(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));
        }
    }

    @Test
    void soOUtilizadorPcmAQuemOProcessoEstaAtribuidoTemDevolverDgtNasAcoes() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410120", "341l");
        Processo processo = atribuidoComLinhaPcm("PDM-B341L", "503410121", sessao);
        String url = "/api/v1/processos/" + processo.getId();

        mockMvc.perform(get(url).with(jwtPcm(sessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", contains("devolver-dgt")));
        mockMvc.perform(get(url).with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("devolver-dgt"))));
        mockMvc.perform(get(url).with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("devolver-dgt"))));
    }

    @Test
    void aNovaValidacaoDeUmProcessoDevolvidoLimpaADataDaDevolucao() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410130", "341m");
        Processo processo = atribuidoComLinhaPcm("PDM-B341M", "503410131", sessao);
        UUID id = processo.getId();
        devolverDgt(id, versao(id), null, jwtPcm(sessao)).andExpect(status().isOk());
        assertThat(processoRepository.findById(id).orElseThrow().getPcmDevolvidoEm()).isNotNull();
        // Anulado pela INCM e corrigido: volta à validação da DGT com a data da devolução anterior.
        comEstado(processoRepository.findById(id).orElseThrow(), EstadoProcesso.EM_VALIDACAO_DGT);

        mockMvc.perform(patch("/api/v1/processos/" + id + "/validar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                        .contentType("application/json")
                        .content(corpoVersao(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"))
                .andExpect(jsonPath("$.pcmDevolvidoEm").doesNotExist());

        assertThat(processoRepository.findById(id).orElseThrow().getPcmDevolvidoEm()).isNull();
        assertThat(registosDevolverDgt(id)).hasSize(1);
    }

    @Test
    void oNovoEnvioAPcmDeUmProcessoDevolvidoAtribuiDeNovoEMantemADataDaUltimaDevolucao() throws Exception {
        SessaoPcm sessao = novaSessaoPcm("503410140", "341n");
        Processo processo = atribuidoComLinhaPcm("PDM-B341N", "503410141", sessao);
        UUID id = processo.getId();
        devolverDgt(id, versao(id), null, jwtPcm(sessao)).andExpect(status().isOk());
        Instant devolvidoEm = processoRepository.findById(id).orElseThrow().getPcmDevolvidoEm();

        enviarPcm(id, versao(id), sessao.utilizador().getId(), jwtComPerfilInterno(UUID.randomUUID(), "TECNICO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pcmAtribuidoAId").value(sessao.utilizador().getId().toString()))
                .andExpect(jsonPath("$.pcmDevolvidoEm").isNotEmpty());

        Processo depois = processoRepository.findById(id).orElseThrow();
        assertThat(depois.getPcmAtribuidoA().getId()).isEqualTo(sessao.utilizador().getId());
        assertThat(depois.getPcmDevolvidoEm()).isEqualTo(devolvidoEm);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    // --- Processos devolvidos pela PCM na fila da DGT (UI-PCM-003, BL-PCM-004) ---

    private record TrioPcm(Processo a, Processo b, Processo c, UUID documentoPcmId, UUID documentoValidadoId,
                           SessaoPcm sessao) {
    }

    /**
     * Três `VALIDADO_DGT` de 1.ª série: A nunca foi à PCM, B está com a PCM e C foi devolvido pela PCM com o texto da
     * RCM que ela acrescentou ainda `POR_VALIDAR` (e um documento já validado pela DGT).
     */
    private TrioPcm trioPcm(String prefixo, String nifBase) throws Exception {
        SessaoPcm sessao = novaSessaoPcm(nifBase + "0", prefixo.toLowerCase());
        Processo a = validadoDgtPrimeiraSerie(prefixo + "A", nifBase + "1", null);
        Processo b = atribuidoComLinhaPcm(prefixo + "B", nifBase + "2", sessao);
        Processo c = atribuidoComLinhaPcm(prefixo + "C", nifBase + "3", sessao);
        ProcessoDocumento validado = new ProcessoDocumento(c, null, CategoriaDocumento.DEPOSITO, "pdf", "deliberacao.pdf",
                "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, c.getCriadoPor());
        validado.setEstadoValidacao(EstadoValidacao.VALIDADO);
        validado = processoDocumentoRepository.saveAndFlush(validado);
        DocumentoConfig item = textoRcm(c, false);
        ProcessoDocumento daPcm = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(c, item,
                CategoriaDocumento.PUBLICACAO, "pdf", "texto-rcm.pdf", "privado/p/" + UUID.randomUUID(), "b".repeat(64), "v1",
                10L, sessao.utilizador()));
        assertThat(daPcm.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        devolverDgt(c.getId(), versao(c.getId()), null, jwtPcm(sessao)).andExpect(status().isOk());
        return new TrioPcm(a, b, c, daPcm.getId(), validado.getId(), sessao);
    }

    private org.springframework.test.web.servlet.ResultActions gravarItem(UUID processoId, String tipo, UUID itemId,
                                                                       String decisao, RequestPostProcessor jwt) throws Exception {
        String corpo = "{\"versao\":" + versao(processoId) + ",\"itens\":[{\"tipo\":\"" + tipo + "\",\"itemId\":\"" + itemId
                + "\",\"decisao\":\"" + decisao + "\",\"notas\":\"Nota de teste.\"}]}";
        return mockMvc.perform(put("/api/v1/processos/" + processoId + "/validacao/itens").with(jwt)
                .contentType("application/json").content(corpo));
    }

    private List<Auditoria> registosRevalidacaoPcm(UUID processoId) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processoId.toString()).stream()
                .filter(linha -> "VALIDAR_ITENS_DEVOLVIDO_PCM".equals(linha.getAcao())).toList();
    }

    @Test
    void oTecnicoListaOsSemPcmEOsDevolvidosComASituacaoEADataEComAPcmSoOQueEstaComAPcm() throws Exception {
        TrioPcm trio = trioPcm("P342A-", "50342001");
        RequestPostProcessor tecnico = jwtComPerfilInterno(UUID.randomUUID(), "TECNICO");
        String c = trio.c().getId().toString();

        mockMvc.perform(get("/api/v1/processos").param("designacaoInstrumento", "P342A-").param("estado", "VALIDADO_DGT")
                        .param("situacaoPcm", "SEM_PCM").param("situacaoPcm", "DEVOLVIDO").with(tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(idsDe(trio.a(), trio.c()))))
                .andExpect(jsonPath("$.content[?(@.id=='" + c + "')].situacaoPcm", contains("DEVOLVIDO")))
                .andExpect(jsonPath("$.content[?(@.id=='" + c + "')].pcmDevolvidoEm", everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.content[?(@.id=='" + trio.a().getId() + "')].situacaoPcm", contains("SEM_PCM")));

        mockMvc.perform(get("/api/v1/processos").param("designacaoInstrumento", "P342A-").param("situacaoPcm", "COM_PCM")
                        .with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(trio.b()))))
                .andExpect(jsonPath("$.content[0].situacaoPcm").value("COM_PCM"))
                .andExpect(jsonPath("$.content[0].pcmDevolvidoEm").doesNotExist());
    }

    @Test
    void paraUmUtilizadorDaCamaraOFiltroSituacaoPcmEIgnoradoEOsCamposVemNulos() throws Exception {
        TrioPcm trio = trioPcm("P342B-", "50342002");
        Entidade camaraDeC = entidadeRepository.findAllByNipc("503420023").get(0);
        Utilizador daCamara = novoUtilizadorIncm("342b1");

        mockMvc.perform(get("/api/v1/processos").param("situacaoPcm", "COM_PCM")
                        .with(jwtExternoDoTipo(camaraDeC, daCamara, "CM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(trio.c()))))
                .andExpect(jsonPath("$.content[0].situacaoPcm").doesNotExist())
                .andExpect(jsonPath("$.content[0].pcmDevolvidoEm").doesNotExist());
        mockMvc.perform(get("/api/v1/processos").param("grupo", "PARA_CORRECAO").param("situacaoPcm", "SEM_PCM")
                        .with(jwtPcm(trio.sessao())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(idsDe(trio.b()))))
                .andExpect(jsonPath("$.content[0].situacaoPcm").doesNotExist());
    }

    @Test
    void umaSituacaoPcmForaDoEnumDa400ValidacaoNoCampo() throws Exception {
        mockMvc.perform(get("/api/v1/processos").param("situacaoPcm", "OUTRO")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("situacaoPcm"));
    }

    @Test
    void aFilaDaDgtContaONuncaEnviadoEODevolvidoENaoOQueEstaComAPcm() throws Exception {
        RequestPostProcessor tecnico = jwtComPerfilInterno(UUID.randomUUID(), "TECNICO");
        long antes = objectMapper.readTree(mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "EM_VALIDACAO")
                .with(tecnico)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("grupos").path(0).path("contagem").asLong();

        TrioPcm trio = trioPcm("P342C-", "50342003");

        mockMvc.perform(get("/api/v1/processos/painel").param("grupo", "EM_VALIDACAO").with(tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].contagem").value(antes + 2));
        mockMvc.perform(get("/api/v1/processos").param("grupo", "EM_VALIDACAO").param("designacaoInstrumento", "P342C-")
                        .with(tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(idsDe(trio.a(), trio.c()))));
    }

    @Test
    void oTecnicoValidaODocumentoDaPcmNoDevolvidoSemMudarOEstadoNemAVersaoEAudita() throws Exception {
        TrioPcm trio = trioPcm("P342D-", "50342004");
        UUID c = trio.c().getId();
        UUID tecnicoId = UUID.randomUUID();
        long versaoAntes = versao(c);
        mockMvc.perform(get("/api/v1/processos/" + c).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revalidacaoPcm").value(true))
                .andExpect(jsonPath("$.pcmDevolvidoEm").isNotEmpty());

        gravarItem(c, "DOCUMENTO", trio.documentoPcmId(), "VALIDADO", jwtComPerfilInterno(tecnicoId, "TECNICO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.itemId=='" + trio.documentoPcmId() + "')].estadoValidacao", contains("VALIDADO")));

        Processo depois = processoRepository.findById(c).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(processoDocumentoRepository.findById(trio.documentoPcmId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.VALIDADO);
        List<Auditoria> registos = registosRevalidacaoPcm(c);
        assertThat(registos).hasSize(1);
        assertThat(registos.get(0).getUtilizadorId()).isEqualTo(tecnicoId);
        assertThat(objectMapper.readTree(registos.get(0).getDadosAlteracao()).path("itens").get(0).asText())
                .isEqualTo(trio.documentoPcmId().toString());
    }

    @Test
    void noDevolvidoARejeicaoEOsItensJaValidadosDao400ENoComAPcmENoSemPcmDa409() throws Exception {
        TrioPcm trio = trioPcm("P342E-", "50342005");
        RequestPostProcessor tecnico = jwtComPerfilInterno(UUID.randomUUID(), "TECNICO");
        UUID c = trio.c().getId();

        gravarItem(c, "DOCUMENTO", trio.documentoPcmId(), "REJEITADO", tecnico)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens[0].decisao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Em Validado DGT o documento só pode ser validado."));
        gravarItem(c, "DOCUMENTO", trio.documentoValidadoId(), "VALIDADO", tecnico)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("itens[0].itemId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Em Validado DGT só se validam os documentos acrescentados pela PCM."));
        assertThat(processoDocumentoRepository.findById(trio.documentoPcmId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(registosRevalidacaoPcm(c)).isEmpty();

        ProcessoDocumento deB = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(trio.b(), null,
                CategoriaDocumento.DEPOSITO, "pdf", "b.pdf", "privado/p/" + UUID.randomUUID(), "c".repeat(64), "v1", 10L,
                trio.sessao().utilizador()));
        gravarItem(trio.b().getId(), "DOCUMENTO", deB.getId(), "VALIDADO", tecnico)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        ProcessoDocumento deA = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(trio.a(), null,
                CategoriaDocumento.DEPOSITO, "pdf", "a.pdf", "privado/p/" + UUID.randomUUID(), "d".repeat(64), "v1", 10L,
                trio.a().getCriadoPor()));
        gravarItem(trio.a().getId(), "DOCUMENTO", deA.getId(), "VALIDADO", tecnico)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(processoDocumentoRepository.findById(deB.getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void comODocumentoDaPcmPorValidarOEnvioAIncmDa409EDepoisDeValidadoSegue() throws Exception {
        TrioPcm trio = trioPcm("P342F-", "50342006");
        UUID c = trio.c().getId();
        UUID tecnicoId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/processos/" + c).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("enviar-incm"))))
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("enviar-pcm"))));
        mockMvc.perform(patch("/api/v1/processos/" + c + "/enviar-incm").with(jwtComPerfilInterno(tecnicoId, "TECNICO"))
                        .contentType("application/json").content(corpoVersao(c)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PCM_DOCUMENTOS_POR_VALIDAR"))
                .andExpect(jsonPath("$.mensagem").value("Existem documentos por validar."));
        verify(envioIncmService, never()).enviar(eq(c), any());
        assertThat(processoRepository.findById(c).orElseThrow().getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);

        gravarItem(c, "DOCUMENTO", trio.documentoPcmId(), "VALIDADO", jwtComPerfilInterno(tecnicoId, "TECNICO"))
                .andExpect(status().isOk());
        envioLevaPara(c, EstadoProcesso.EM_VALIDACAO_INCM);
        mockMvc.perform(patch("/api/v1/processos/" + c + "/enviar-incm").with(jwtComPerfilInterno(tecnicoId, "TECNICO"))
                        .contentType("application/json").content(corpoVersao(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_INCM"));
    }

    @Test
    void comODocumentoValidadoODetalheTemEnviarIncmEEnviarPcmESemRevalidacaoQueSoADgtVe() throws Exception {
        TrioPcm trio = trioPcm("P342G-", "50342007");
        UUID c = trio.c().getId();
        UUID tecnicoId = UUID.randomUUID();
        Utilizador daCamara = novoUtilizadorIncm("342g1");
        mockMvc.perform(get("/api/v1/processos/" + c).with(jwtExternoDoTipo(entidadeRepository.findAllByNipc("503420073").get(0), daCamara, "CM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revalidacaoPcm").value(false));
        mockMvc.perform(get("/api/v1/processos/" + c).with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revalidacaoPcm").value(false));

        gravarItem(c, "DOCUMENTO", trio.documentoPcmId(), "VALIDADO", jwtComPerfilInterno(tecnicoId, "TECNICO"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + c).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", hasItems("enviar-incm", "enviar-pcm")))
                .andExpect(jsonPath("$.revalidacaoPcm").value(false));
        mockMvc.perform(get("/api/v1/processos/" + trio.a().getId()).with(jwtComPerfilInterno(tecnicoId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis", hasItem("enviar-incm")))
                .andExpect(jsonPath("$.revalidacaoPcm").value(false));
    }
}
