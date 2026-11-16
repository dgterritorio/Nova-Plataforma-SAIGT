package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
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
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base H2 partilhada pelos testes com as mesmas propriedades: cada teste cria os seus tipos com códigos únicos. */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracaoEstruturaControllerIntegrationTest {

    private static final String DINAMICAS = "/api/v1/configuracao/dinamicas-config";
    private static final String PROCEDIMENTOS = "/api/v1/configuracao/procedimentos-config";
    private static final long CINCO_GB = 5_368_709_120L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
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
    private AuditoriaRepository auditoriaRepository;

    private static String codigoUnico() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static RequestPostProcessor comoMaster() {
        return comoPerfil("MASTER");
    }

    private static RequestPostProcessor comoPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private TipoInstrumento tipoInstrumento(String codigo, String nome) {
        return tipoInstrumentoRepository.findAll().stream().filter(t -> t.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigo, nome, "PROGRAMA")));
    }

    private TipoDinamica tipoDinamica(String codigo, String nome) {
        return tipoDinamicaRepository.findAll().stream().filter(t -> t.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigo, nome)));
    }

    private TipoProcedimento tipoProcedimento(String codigo, String nome) {
        return tipoProcedimentoRepository.findAll().stream().filter(t -> t.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, nome)));
    }

    private InstrumentoDinamicaConfig novaConfigDinamica() {
        return dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico().substring(0, 12), "Tipo", "PLANO")),
                tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Dinâmica"))));
    }

    private String json(Map<String, Object> corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private static Map<String, Object> corpoDinamica(Object tipoInstrumentoId, Object tipoDinamicaId, Object associacao) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("tipoInstrumentoId", tipoInstrumentoId);
        corpo.put("tipoDinamicaId", tipoDinamicaId);
        corpo.put("associacaoInstrumentos", associacao);
        corpo.put("ordem", 1);
        corpo.put("ativo", true);
        return corpo;
    }

    private static Map<String, Object> corpoProcedimento(Object dinamicaConfigId, Object tipoProcedimentoId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoDinamicaConfigId", dinamicaConfigId);
        corpo.put("tipoProcedimentoId", tipoProcedimentoId);
        corpo.put("ordem", 1);
        corpo.put("obrigatorio", true);
        corpo.put("serieDr", "SEGUNDA_H");
        corpo.put("tamanhoMaximoProcesso", CINCO_GB);
        corpo.put("repetivel", false);
        corpo.put("atoPontual", false);
        corpo.put("ativo", true);
        return corpo;
    }

    private ResultActions postar(String url, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(url).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)));
    }

    @Test
    void masterDeveCriarARevisaoDoProtSemUsoEOWorkflowDevePassarALista_la() throws Exception {
        TipoInstrumento prot = tipoInstrumento("PROT", "Programa Regional de Ordenamento do Território");
        TipoDinamica revisao = tipoDinamica("REVISAO", "Revisão");

        String resposta = postar(DINAMICAS, corpoDinamica(prot.getId(), revisao.getId(), "UM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tipoInstrumento.codigo").value("PROT"))
                .andExpect(jsonPath("$.tipoDinamica.codigo").value("REVISAO"))
                .andExpect(jsonPath("$.associacaoInstrumentos").value("UM"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.emUso").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get("/api/v1/workflows").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].titulo").value("PROT — Revisão"));
        mockMvc.perform(get(DINAMICAS).param("tipoInstrumentoId", String.valueOf(prot.getId())).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].emUso").value(false));
    }

    @Test
    void masterDeveCriarADecisaoDeInicioNaRevisaoDoProtComOFormularioDoTipo() throws Exception {
        InstrumentoDinamicaConfig revisaoProt = novaConfigDinamica();
        TipoProcedimento decisao = tipoProcedimento("DECISAO_INICIO", "Decisão de Início");

        String resposta = postar(PROCEDIMENTOS, corpoProcedimento(revisaoProt.getId(), decisao.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentoDinamicaConfigId").value(revisaoProt.getId()))
                .andExpect(jsonPath("$.tipoProcedimento.codigo").value("DECISAO_INICIO"))
                .andExpect(jsonPath("$.ordem").value(1))
                .andExpect(jsonPath("$.obrigatorio").value(true))
                .andExpect(jsonPath("$.serieDr").value("SEGUNDA_H"))
                .andExpect(jsonPath("$.tamanhoMaximoProcesso").value(CINCO_GB))
                .andExpect(jsonPath("$.repetivel").value(false))
                .andExpect(jsonPath("$.atoPontual").value(false))
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                .andExpect(jsonPath("$.emUso").value(false))
                .andExpect(jsonPath("$.visivelNaDinamica").value(false))
                .andExpect(jsonPath("$.prazoDias").doesNotExist())
                .andExpect(jsonPath("$.tipoPrazo").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resposta).get("id").asLong();

        Map<String, Object> linha = jdbcTemplate.queryForMap("SELECT serie_dr, tamanho_maximo_processo, repetivel, "
                + "ato_pontual, obrigatorio, ordem FROM instrumento_procedimento_config WHERE id = ?", id);
        assertThat(linha.get("serie_dr")).isEqualTo("SEGUNDA_H");
        assertThat(((Number) linha.get("tamanho_maximo_processo")).longValue()).isEqualTo(CINCO_GB);
        assertThat(linha.get("repetivel")).isEqualTo(false);
        assertThat(linha.get("ato_pontual")).isEqualTo(false);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM permissao_acao WHERE instrumento_procedimento_config_id = ?",
                Integer.class, id)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM documento_config WHERE instrumento_procedimento_config_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    void pedidosInvalidosDevemDar400ComOCampoAssinalado() throws Exception {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        TipoProcedimento tipo = tipoProcedimento(codigoUnico(), "Procedimento");

        Map<String, Object> serieTerceira = corpoProcedimento(config.getId(), tipo.getId());
        serieTerceira.put("serieDr", "TERCEIRA");
        Map<String, Object> ordemZero = corpoProcedimento(config.getId(), tipo.getId());
        ordemZero.put("ordem", 0);
        Map<String, Object> tamanhoZero = corpoProcedimento(config.getId(), tipo.getId());
        tamanhoZero.put("tamanhoMaximoProcesso", 0);
        Map<String, Object> tipoInexistente = corpoProcedimento(config.getId(), 999_999_999L);
        Map<String, Object> configInexistente = corpoProcedimento(999_999_999L, tipo.getId());
        Map<String, Object> semObrigatorio = corpoProcedimento(config.getId(), tipo.getId());
        semObrigatorio.remove("obrigatorio");

        Map<Map<String, Object>, String> casos = new java.util.LinkedHashMap<>();
        casos.put(serieTerceira, "serieDr");
        casos.put(ordemZero, "ordem");
        casos.put(tamanhoZero, "tamanhoMaximoProcesso");
        casos.put(tipoInexistente, "tipoProcedimentoId");
        casos.put(configInexistente, "instrumentoDinamicaConfigId");
        casos.put(semObrigatorio, "obrigatorio");
        for (Map.Entry<Map<String, Object>, String> caso : casos.entrySet()) {
            postar(PROCEDIMENTOS, caso.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value(caso.getValue()));
        }

        TipoInstrumento tipoInstrumento = config.getTipoInstrumento();
        postar(DINAMICAS, corpoDinamica(tipoInstrumento.getId(), tipoDinamica(codigoUnico(), "D").getId(), "VARIOS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("associacaoInstrumentos"));
        postar(DINAMICAS, corpoDinamica(tipoInstrumento.getId(), 999_999_999L, "UM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoDinamicaId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Tipo de Dinâmica inexistente."));
        postar(DINAMICAS, corpoDinamica(999_999_999L, config.getTipoDinamica().getId(), "UM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoInstrumentoId"));
    }

    @Test
    void parRepetidoDeveDar400AntesDaBase() throws Exception {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        TipoProcedimento tipo = tipoProcedimento(codigoUnico(), "Procedimento");
        procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(config, tipo, 1, "SEGUNDA_H"));

        postar(DINAMICAS, corpoDinamica(config.getTipoInstrumento().getId(), config.getTipoDinamica().getId(), "UM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoDinamicaId"));
        postar(PROCEDIMENTOS, corpoProcedimento(config.getId(), tipo.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoProcedimentoId"));
    }

    @Test
    void tecnicoDeveDar403EmTodosOsEndpointsESemSessao401() throws Exception {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        TipoProcedimento tipo = tipoProcedimento(codigoUnico(), "Procedimento");
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(config, tipo, 1, "SEGUNDA_H"));
        String corpoDinamica = json(corpoDinamica(config.getTipoInstrumento().getId(), config.getTipoDinamica().getId(), "UM"));
        String corpoProcedimento = json(corpoProcedimento(config.getId(), tipo.getId()));

        for (RequestPostProcessor ator : List.of(comoPerfil("TECNICO"), comoPerfil("VISITANTE"))) {
            List<ResultActions> respostas = List.of(
                    mockMvc.perform(get(DINAMICAS).with(ator)),
                    mockMvc.perform(post(DINAMICAS).with(ator).contentType(APPLICATION_JSON).content(corpoDinamica)),
                    mockMvc.perform(put(DINAMICAS + "/" + config.getId()).with(ator).contentType(APPLICATION_JSON)
                            .content(corpoDinamica)),
                    mockMvc.perform(get(PROCEDIMENTOS).with(ator)),
                    mockMvc.perform(post(PROCEDIMENTOS).with(ator).contentType(APPLICATION_JSON).content(corpoProcedimento)),
                    mockMvc.perform(put(PROCEDIMENTOS + "/" + procedimento.getId()).with(ator)
                            .contentType(APPLICATION_JSON).content(corpoProcedimento)));
            for (ResultActions resposta : respostas) {
                resposta.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            }
        }
        mockMvc.perform(get(DINAMICAS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROCEDIMENTOS)).andExpect(status().isUnauthorized());
    }

    @Test
    void cadaEscritaDeveFicarAuditadaComORecursoEOId() throws Exception {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigoUnico().substring(0, 12), "Tipo", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamica(codigoUnico(), "Dinâmica");
        long dinamicaId = objectMapper.readTree(postar(DINAMICAS, corpoDinamica(tipoInstrumento.getId(), tipoDinamica.getId(), "UM"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put(DINAMICAS + "/" + dinamicaId).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(corpoDinamica(tipoInstrumento.getId(), tipoDinamica.getId(), "OPCIONAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associacaoInstrumentos").value("OPCIONAL"));

        TipoProcedimento tipo = tipoProcedimento(codigoUnico(), "Procedimento");
        long procedimentoId = objectMapper.readTree(postar(PROCEDIMENTOS, corpoProcedimento(dinamicaId, tipo.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        Map<String, Object> alterado = corpoProcedimento(dinamicaId, tipo.getId());
        alterado.put("ordem", 2);
        mockMvc.perform(put(PROCEDIMENTOS + "/" + procedimentoId).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(alterado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordem").value(2));

        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", "dinamicas-config:" + dinamicaId))
                .extracting(Auditoria::getAcao).containsExactlyInAnyOrder("CRIAR", "ATUALIZAR");
        List<Auditoria> procedimento = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "procedimentos-config:" + procedimentoId);
        assertThat(procedimento).extracting(Auditoria::getAcao).containsExactlyInAnyOrder("CRIAR", "ATUALIZAR");
        assertThat(procedimento).allSatisfy(linha -> assertThat(linha.getUtilizadorId()).isNotNull());
    }

    @Test
    void listagemDeveVirPorOrdemComOEmUsoSoNosComProcessosESemCrescerAsConsultas() throws Exception {
        Cenario pequeno = cenarioComProcessoNoSegundo(2);
        Cenario grande = cenarioComProcessoNoSegundo(7);

        String pagina = mockMvc.perform(get(PROCEDIMENTOS).param("instrumentoDinamicaConfigId",
                        String.valueOf(grande.dinamicaConfig().getId())).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andReturn().getResponse().getContentAsString();
        JsonNode conteudo = objectMapper.readTree(pagina).get("content");
        for (int i = 0; i < conteudo.size(); i++) {
            assertThat(conteudo.get(i).get("ordem").asInt()).isEqualTo(i + 1);
            assertThat(conteudo.get(i).get("emUso").asBoolean()).isEqualTo(i == 1);
        }

        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(get(PROCEDIMENTOS).param("instrumentoDinamicaConfigId",
                String.valueOf(pequeno.dinamicaConfig().getId())).with(comoMaster())).andExpect(status().isOk());
        long consultasPequeno = estatisticas.getPrepareStatementCount();
        estatisticas.clear();
        mockMvc.perform(get(PROCEDIMENTOS).param("instrumentoDinamicaConfigId",
                String.valueOf(grande.dinamicaConfig().getId())).with(comoMaster())).andExpect(status().isOk());
        long consultasGrande = estatisticas.getPrepareStatementCount();

        assertThat(consultasGrande).isEqualTo(consultasPequeno);

        estatisticas.clear();
        mockMvc.perform(get(DINAMICAS).param("size", "100").with(comoMaster())).andExpect(status().isOk());
        long consultasDinamicas = estatisticas.getPrepareStatementCount();
        assertThat(consultasDinamicas).isLessThanOrEqualTo(consultasGrande);
    }

    @Test
    void desativarOProcedimentoNaoImpedeOAutorDeEditarESubmeterOProcessoEmSubmissao() throws Exception {
        Cenario cenario = cenarioComProcessoNoSegundo(2);
        InstrumentoProcedimentoConfig procedimento = cenario.procedimentoComProcesso();
        Map<String, Object> desativar = corpoProcedimento(cenario.dinamicaConfig().getId(),
                procedimento.getTipoProcedimento().getId());
        desativar.put("ordem", procedimento.getOrdem());
        desativar.put("tamanhoMaximoProcesso", null);
        desativar.put("ativo", false);

        mockMvc.perform(put(PROCEDIMENTOS + "/" + procedimento.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(desativar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.emUso").value(true));
        Processo processo = cenario.processo();
        assertThat(processoRepository.findById(processo.getId()).orElseThrow().getEstado().name()).isEqualTo("EM_SUBMISSAO");

        String base = "{\"versao\":%d,\"sumario\":\"Resumo editado.\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";
        mockMvc.perform(put("/api/v1/processos/" + processo.getId() + "/dados-formulario")
                        .with(jwtDoAutor(cenario)).contentType(APPLICATION_JSON)
                        .content(String.format(base, versao(processo)) + ",\"entidadeEmitente\":\"Camara Municipal de Teste\","
                                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/submeter")
                        .with(jwtDoAutor(cenario)).contentType(APPLICATION_JSON)
                        .content(String.format(base, versao(processo)) + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(processoRepository.findById(processo.getId()).orElseThrow().getSumario()).isEqualTo("Resumo editado.");
    }

    private record Cenario(InstrumentoDinamicaConfig dinamicaConfig, InstrumentoProcedimentoConfig procedimentoComProcesso,
                           Processo processo, Utilizador autor, Entidade entidade) {
    }

    private long versao(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getVersao();
    }

    /** Procedimentos 1..n gravados pela ordem inversa; só o de ordem 2 tem um processo, em submissão, da CM. */
    private Cenario cenarioComProcessoNoSegundo(int procedimentos) {
        InstrumentoDinamicaConfig config = novaConfigDinamica();
        InstrumentoProcedimentoConfig segundo = null;
        for (int ordem = procedimentos; ordem >= 1; ordem--) {
            TipoProcedimento tipo = ordem == 2
                    ? tipoProcedimento("ALTERACAO_SIMPLIFICADA_DL10", "Alteração Simplificada")
                    : tipoProcedimento(codigoUnico(), "Procedimento " + ordem);
            InstrumentoProcedimentoConfig linha = procedimentoConfigRepository.saveAndFlush(
                    new InstrumentoProcedimentoConfig(config, tipo, ordem, "SEGUNDA_H"));
            if (ordem == 2) {
                segundo = linha;
            }
        }
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, segundo));

        String nipc = String.valueOf(500_000_000L + (long) (Math.random() * 99_999_999L));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nipc, "Câmara de Teste"));
        String sufixo = codigoUnico();
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora", "autora" + sufixo.toLowerCase() + "@cm-teste.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(config.getTipoInstrumento(), "1", "2026/CFG/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, config, "Alteração", autor));
        Processo processo = new Processo(dinamica, segundo, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Camara Municipal de Teste");
        return new Cenario(config, segundo, processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        Entidade entidade = cenario.entidade();
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }
}
