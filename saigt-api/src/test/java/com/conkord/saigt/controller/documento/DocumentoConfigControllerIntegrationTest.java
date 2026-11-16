package com.conkord.saigt.controller.documento;

import com.conkord.saigt.db.PostgresTestcontainersBase;
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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
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
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.ExtensoesAceites;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Na base real, partilhada e sem rollback por toda a suite: cada teste cria o seu Procedimento e códigos únicos. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentoConfigControllerIntegrationTest extends PostgresTestcontainersBase {

    private static final String URL = "/api/v1/documento-config";
    private static final String HASH = "a".repeat(64);

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
    private DocumentoConfigRepository documentoConfigRepository;
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
    private ProcessoDocumentoRepository processoDocumentoRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade,
                           InstrumentoProcedimentoConfig procedimento) {
    }

    private static String sufixoUnico() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private Cenario criarCenario() {
        String sufixo = sufixoUnico();
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("D" + sufixo, "Dinâmica"));
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("I" + sufixo, "Instrumento " + sufixo, "PROGRAMA"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("P" + sufixo, "Procedimento " + sufixo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        String nipc = String.valueOf(ThreadLocalRandom.current().nextInt(500_000_000, 599_999_999));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nipc, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-dcfg.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DCFG/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        return new Cenario(processo, autor, entidade, procedimento);
    }

    private DocumentoConfig criarItem(Cenario cenario, String codigo, String separador, CategoriaDocumento categoria,
                                      TipoElemento tipoElemento, int ordem) {
        DocumentoConfig item = new DocumentoConfig(cenario.procedimento(), codigo, "Item " + codigo, separador,
                categoria, tipoElemento, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", ordem);
        item.setLinkPublicoPermitido(true);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private static RequestPostProcessor comoMaster() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private static RequestPostProcessor comoExterno(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, cenario.autor().getId().toString())
                .claim("origem", "INCM").claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(),
                        "nif", cenario.entidade().getNipc(),
                        "nome", cenario.entidade().getDescricaoIncm(), "pcm", false)));
    }

    private Map<String, Object> corpoValido(Long procedimentoId, String codigo) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", procedimentoId);
        corpo.put("codigo", codigo);
        corpo.putAll(corpoAtualizacao());
        return corpo;
    }

    private Map<String, Object> corpoAtualizacao() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", "Deliberação");
        corpo.put("separador", "Deliberação");
        corpo.put("categoria", "PUBLICACAO");
        corpo.put("tipoElemento", "PECA_ESCRITA");
        corpo.put("formato", "EDITAVEL");
        corpo.put("extensoesAceites", List.of(".PDF", "docx"));
        corpo.put("obrigatorio", true);
        corpo.put("ordem", 1);
        corpo.put("elementoInformacaoBase", "DELIBERACAO");
        corpo.put("linkPublicoPermitido", true);
        corpo.put("ativo", true);
        return corpo;
    }

    private ResultActions criar(Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(URL).with(comoMaster()).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions atualizar(Long id, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(URL + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private Map<String, Object> corpoPlantaGrafica() {
        Map<String, Object> corpo = corpoAtualizacao();
        corpo.put("tipoElemento", "PECA_GRAFICA");
        corpo.put("formato", "NAO_EDITAVEL");
        corpo.put("extensoesAceites", List.of("zip"));
        corpo.put("elementoInformacaoBase", null);
        return corpo;
    }

    @Test
    void masterDeveCriarItemNormalizarExtensoesVeloNaEstruturaDocumentalEAuditar() throws Exception {
        Cenario cenario = criarCenario();

        String resposta = criar(corpoValido(cenario.procedimento().getId(), "DELIBERACAO_PUB"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.extensoesAceites[0]").value("pdf"))
                .andExpect(jsonPath("$.extensoesAceites[1]").value("docx"))
                .andExpect(jsonPath("$.linkPublicoPermitido").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(comoExterno(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].separador").value("Deliberação"))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].codigo").value("DELIBERACAO_PUB"));

        DocumentoConfig gravado = documentoConfigRepository.findById(id).orElseThrow();
        assertThat(ExtensoesAceites.ler(gravado)).containsExactly("pdf", "docx");
        assertThat(gravado.getExtensoesAceites()).doesNotContain(".", "PDF");
        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "documento-config:" + id);
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactly("CRIAR");
        assertThat(linhas.get(0).getUtilizadorId()).isNotNull();
    }

    @Test
    void pedidoSemLinkPublicoPermitidoDeveDar400NoCampo() throws Exception {
        Cenario cenario = criarCenario();
        Map<String, Object> corpo = corpoValido(cenario.procedimento().getId(), "SEM_LINK");
        corpo.remove("linkPublicoPermitido");

        criar(corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[?(@.path=='linkPublicoPermitido')]").isNotEmpty());
    }

    @Test
    void extensaoForaDaTabelaDaEfDeveDar400ConfigExtensaoNaoAceite() throws Exception {
        Cenario cenario = criarCenario();
        Map<String, Object> corpo = corpoValido(cenario.procedimento().getId(), "JPG_PUB");
        corpo.put("extensoesAceites", List.of("jpg"));

        criar(corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONFIG_EXTENSAO_NAO_ACEITE"))
                .andExpect(jsonPath("$.campos[0].path").value("extensoesAceites"));
    }

    @Test
    void pecaEscritaDeDepositoEditavelDeveDar400FormatoIncoerenteMasPecaGraficaNao() throws Exception {
        Cenario cenario = criarCenario();
        Map<String, Object> escrita = corpoValido(cenario.procedimento().getId(), "TEXTO_DEP");
        escrita.put("categoria", "DEPOSITO");
        escrita.put("extensoesAceites", List.of("pdf"));

        criar(escrita).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONFIG_FORMATO_INCOERENTE"))
                .andExpect(jsonPath("$.campos[0].path").value("formato"));

        Map<String, Object> grafica = corpoValido(cenario.procedimento().getId(), "PLANTA_DEP");
        grafica.put("categoria", "DEPOSITO");
        grafica.put("tipoElemento", "PECA_GRAFICA");
        grafica.put("formato", "EDITAVEL");
        grafica.put("extensoesAceites", List.of("zip"));
        grafica.put("elementoInformacaoBase", null);
        criar(grafica).andExpect(status().isCreated());
    }

    @Test
    void informacaoBaseDiferenteNoMesmoSeparadorDeveDar400() throws Exception {
        Cenario cenario = criarCenario();
        criar(corpoValido(cenario.procedimento().getId(), "DELIBERACAO_PUB")).andExpect(status().isCreated());
        Map<String, Object> segundo = corpoValido(cenario.procedimento().getId(), "DELIBERACAO_PUB_2");
        segundo.put("elementoInformacaoBase", "TEXTO_ATO");

        criar(segundo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("elementoInformacaoBase"));
    }

    @Test
    void camposInvalidosDevemDar400ComOCampoAssinalado() throws Exception {
        Cenario cenario = criarCenario();
        criar(corpoValido(cenario.procedimento().getId(), "REPETIDO")).andExpect(status().isCreated());

        criar(corpoValido(cenario.procedimento().getId(), "REPETIDO")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));

        Map<String, Object> semExtensoes = corpoValido(cenario.procedimento().getId(), "SEM_EXT");
        semExtensoes.put("extensoesAceites", List.of());
        criar(semExtensoes).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.path=='extensoesAceites')]").isNotEmpty());

        Map<String, Object> ordemZero = corpoValido(cenario.procedimento().getId(), "ORDEM_ZERO");
        ordemZero.put("ordem", 0);
        criar(ordemZero).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.path=='ordem')]").isNotEmpty());

        Map<String, Object> limiteZero = corpoValido(cenario.procedimento().getId(), "LIMITE_ZERO");
        limiteZero.put("tamanhoMaximoFicheiro", 0);
        criar(limiteZero).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.path=='tamanhoMaximoFicheiro')]").isNotEmpty());

        criar(corpoValido(-1L, "SEM_PROCEDIMENTO")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoProcedimentoConfigId"));
    }

    @Test
    void itemComFicheirosNaoMudaDeCategoriaMasMudaOsOutrosCampos() throws Exception {
        Cenario cenario = criarCenario();
        DocumentoConfig item = criarItem(cenario, "PLANTA_LOCALIZACAO_PUB", "Planta", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, 1);
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), item,
                CategoriaDocumento.PUBLICACAO, "zip", "planta.zip", "privado/dcfg/" + UUID.randomUUID(), HASH, "v1",
                10, cenario.autor()));

        Map<String, Object> mudaCategoria = corpoPlantaGrafica();
        mudaCategoria.put("categoria", "DEPOSITO");
        atualizar(item.getId(), mudaCategoria).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));
        assertThat(documentoConfigRepository.findById(item.getId()).orElseThrow().getCategoria())
                .isEqualTo(CategoriaDocumento.PUBLICACAO);

        Map<String, Object> outros = corpoPlantaGrafica();
        outros.put("nome", "Planta de localização");
        outros.put("obrigatorio", false);
        outros.put("linkPublicoPermitido", false);
        outros.put("tamanhoMaximoFicheiro", 5_000_000);
        outros.put("tamanhoMaximoTotal", 10_000_000);
        outros.put("numeroMaximoFicheiros", 3);
        atualizar(item.getId(), outros).andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Planta de localização"))
                .andExpect(jsonPath("$.linkPublicoPermitido").value(false))
                .andExpect(jsonPath("$.tamanhoMaximoFicheiro").value(5_000_000))
                .andExpect(jsonPath("$.numeroMaximoFicheiros").value(3));
    }

    @Test
    void putComCodigoNoCorpoNaoMudaOCodigoGravado() throws Exception {
        Cenario cenario = criarCenario();
        DocumentoConfig item = criarItem(cenario, "CODIGO_FIXO", "Planta", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, 1);
        Map<String, Object> corpo = corpoPlantaGrafica();
        corpo.put("codigo", "OUTRO_CODIGO");
        corpo.put("instrumentoProcedimentoConfigId", -5);

        atualizar(item.getId(), corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("CODIGO_FIXO"))
                .andExpect(jsonPath("$.instrumentoProcedimentoConfigId").value(cenario.procedimento().getId()));
        assertThat(documentoConfigRepository.findById(item.getId()).orElseThrow().getCodigo()).isEqualTo("CODIGO_FIXO");
    }

    @Test
    void putDeIdInexistenteDeveDar404() throws Exception {
        atualizar(Long.MAX_VALUE, corpoPlantaGrafica()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void externoPodeListarMasNaoEscreverEListarSemProcedimentoDa400() throws Exception {
        Cenario cenario = criarCenario();
        DocumentoConfig item = criarItem(cenario, "LISTADO", "Planta", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, 1);

        mockMvc.perform(get(URL).param("instrumentoProcedimentoConfigId", String.valueOf(cenario.procedimento().getId()))
                        .with(comoExterno(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("LISTADO"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(post(URL).with(comoExterno(cenario)).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido(cenario.procedimento().getId(), "EXT"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(put(URL + "/" + item.getId()).with(comoExterno(cenario)).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoPlantaGrafica())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get(URL).with(comoExterno(cenario))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("instrumentoProcedimentoConfigId", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void listarDeveOrdenarPorOrdemEFiltrarPorAtivo() throws Exception {
        Cenario cenario = criarCenario();
        criarItem(cenario, "SEGUNDO", "B", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, 2);
        criarItem(cenario, "PRIMEIRO", "A", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, 1);
        DocumentoConfig inativo = criarItem(cenario, "INATIVO", "C", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, 3);
        inativo.setAtivo(false);
        documentoConfigRepository.saveAndFlush(inativo);
        String id = String.valueOf(cenario.procedimento().getId());

        String todos = mockMvc.perform(get(URL).param("instrumentoProcedimentoConfigId", id).with(comoMaster()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode conteudo = objectMapper.readTree(todos).get("content");
        assertThat(conteudo).extracting(n -> n.get("codigo").asText()).containsExactly("PRIMEIRO", "SEGUNDO", "INATIVO");

        mockMvc.perform(get(URL).param("instrumentoProcedimentoConfigId", id).param("ativo", "false")
                        .with(comoMaster()))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].codigo").value("INATIVO"));
    }

    @Test
    void desativarDeveRetirarOItemDaEstruturaDocumentalEAuditarSoOQueMudou() throws Exception {
        Cenario cenario = criarCenario();
        DocumentoConfig item = criarItem(cenario, "TEXTO_ATO_DEP", "Texto", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, 1);
        Map<String, Object> corpo = corpoPlantaGrafica();
        corpo.put("categoria", "DEPOSITO");
        corpo.put("nome", "Item " + item.getCodigo());
        corpo.put("separador", "Texto");
        corpo.put("ordem", 1);
        corpo.put("linkPublicoPermitido", true);
        corpo.put("obrigatorio", false);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(comoExterno(cenario)))
                .andExpect(jsonPath("$.separadores.length()").value(1));

        corpo.put("ativo", false);
        atualizar(item.getId(), corpo).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(comoExterno(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(0));
        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "documento-config:" + item.getId());
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactly("ATUALIZAR");
        assertThat(linhas.get(0).getDadosAlteracao()).contains("ativo").doesNotContain("nome");
    }
}
