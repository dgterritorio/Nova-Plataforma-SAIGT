package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.documento.GravarPlantasRequest;
import com.conkord.saigt.dto.documento.GravarPlantasRequest.AssociacaoPlanta;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
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
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A página das Plantas nos procedimentos finais do PDM: Elaboração e Revisão (como o 201) e Publicação da Alteração. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantasProcedimentosFinaisIntegrationTest {

    private static final String HASH = "c".repeat(64);
    private static final String TXT_ORDENAMENTO = "Ordenamento; Planta de Ordenamento – Uso do Solo; A\n"
            + "Ordenamento; Planta de Ordenamento – Uso do Solo; B\n";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9700);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ArmazenamentoDocumentos armazenamento;

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private CompletudeDocumentalService completudeDocumentalService;

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

    private final Map<String, byte[]> objetos = new HashMap<>();

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, Map<String, DocumentoConfig> itens) {
    }

    @BeforeEach
    void armazenamentoEmMemoria() {
        when(armazenamento.abrir(anyString(), any(), anyLong(), anyLong()))
                .thenAnswer(pedido -> new ByteArrayInputStream(objetos.get(pedido.<String>getArgument(0))));
    }

    /** Um procedimento final do PDM: as plantas de depósito na lista de documentos e os quatro itens da página das Plantas. */
    private Cenario cenario(String tipoDinamica, Boolean comGeoconform, List<String> elementosAfetados) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PLF-" + n, "Plano " + n, "PLANO"));
        TipoDinamica tipo = tipoDinamicaRepository.findAll().stream().filter(candidato -> tipoDinamica.equals(candidato.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica(tipoDinamica, tipoDinamica)));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipo));
        TipoProcedimento procedimentoFinal = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PLF-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, procedimentoFinal, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Map<String, DocumentoConfig> itens = new HashMap<>();
        itens.put("TEXTO", item(procedimento, "TEXTO_PUB", "Texto do Ato", "Texto do Ato", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, "[\"pdf\"]", true, 1, PaginaDocumento.DOCUMENTOS));
        itens.put("ORDENAMENTO_PUB", item(procedimento, "PLANTA_ORDENAMENTO_PUB", "Planta de Ordenamento", "Planta de ordenamento",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 7, PaginaDocumento.DOCUMENTOS));
        itens.put("ORDENAMENTO_DEP", item(procedimento, "PLANTA_ORDENAMENTO_DEP", "Planta de Ordenamento", "Planta de ordenamento",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 8, PaginaDocumento.DOCUMENTOS));
        itens.put("CONDICIONANTES_DEP", item(procedimento, "PLANTA_CONDICIONANTES_DEP", "Planta de Condicionantes",
                "Planta de condicionantes", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 10,
                PaginaDocumento.DOCUMENTOS));
        itens.put("RELATORIO_DEP", item(procedimento, "RELATORIO_DEP", "Relatório", "Relatório", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, "[\"pdf\"]", false, 12, PaginaDocumento.DOCUMENTOS));
        itens.put("ORDENAMENTO", item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", "Planta de Ordenamento", "Planta de ordenamento",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 30, PaginaDocumento.PLANTAS));
        itens.put("CONDICIONANTES", item(procedimento, "PLANTAS_ZIP_CONDICIONANTES_DEP", "Planta de Condicionantes",
                "Planta de condicionantes", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 31,
                PaginaDocumento.PLANTAS));
        itens.put("TXT", item(procedimento, "PLANTAS_TXT_NOMES_DEP", "Nomes das plantas", "Nomes das plantas conforme o regulamento",
                CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, "[\"txt\"]", false, 32, PaginaDocumento.PLANTAS));
        itens.put("CARTOGRAMA", item(procedimento, "PLANTAS_CARTOGRAMA_DEP", "Cartograma das folhas", "Cartograma das folhas",
                CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, "[\"png\"]", false, 33, PaginaDocumento.PLANTAS));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("509" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-plf.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor);
        dinamica.setComGeoconform(comGeoconform);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setElementosAfetados(elementosAfetados);
        processo = processoRepository.saveAndFlush(processo);
        return new Cenario(processo, autor, entidade, itens);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, String separador, String nome,
                                 CategoriaDocumento categoria, TipoElemento tipoElemento, String extensoes, boolean obrigatorio,
                                 int ordem, PaginaDocumento pagina) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, nome, separador, categoria, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, extensoes, ordem);
        item.setObrigatorio(obrigatorio);
        item.setPagina(pagina);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private void carregar(Cenario cenario, String item, String nome, byte[] conteudo) {
        String caminho = "privado/plf/" + UUID.randomUUID() + "-" + nome;
        objetos.put(caminho, conteudo);
        DocumentoConfig config = cenario.itens().get(item);
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), config, config.getCategoria(),
                nome.substring(nome.lastIndexOf('.') + 1), nome, caminho, HASH, "v1", conteudo.length, cenario.autor()));
    }

    private void carregarZipOrdenamento(Cenario cenario) throws IOException {
        carregar(cenario, "ORDENAMENTO", "ordenamento.zip", zip("1199_Ord_A.tif", "1199_Ord_B.tif"));
    }

    private void carregarTxtOrdenamento(Cenario cenario) {
        carregar(cenario, "TXT", "nomes.txt", TXT_ORDENAMENTO.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(String... nomes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String nome : nomes) {
                zip.putNextEntry(new ZipEntry(nome));
                zip.write(new byte[] {1});
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static RequestPostProcessor jwtDe(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false)));
    }

    private ResultActions pedirPlantas(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/plantas").with(jwtDe(cenario)));
    }

    private ResultActions pedirEstrutura(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental").with(jwtDe(cenario)));
    }

    private ResultActions gravarPlantas(Cenario cenario, AssociacaoPlanta... associacoes) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/plantas").with(jwtDe(cenario))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new GravarPlantasRequest(List.of(associacoes)))));
    }

    private List<CampoErro> pendenciasDasPlantas(Cenario cenario) {
        return transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream()
                .filter(campo -> campo.path().startsWith("plantas.")).toList());
    }

    private List<String> caminhosPendentes(Cenario cenario) {
        return pendenciasDasPlantas(cenario).stream().map(CampoErro::path).toList();
    }

    private boolean exigePlantasEMetadados(Cenario cenario) {
        return transacao.execute(estado -> completudeDocumentalService.exigePlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()));
    }

    @Test
    void naElaboracaoSemGeoconformAsPlantasSaemDaListaEAbrePaginaComTodosOsZipObrigatorios() throws Exception {
        Cenario cenario = cenario("ELABORACAO", false, null);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Planta de Ordenamento", "Relatório")))
                .andExpect(jsonPath("$.separadores[1].deposito.length()").value(0))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(contains("TEXTO_PUB")));
        assertThat(exigePlantasEMetadados(cenario)).isTrue();
        pedirPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigePlantas").value(true))
                .andExpect(jsonPath("$.itens[*].codigo").value(contains("PLANTAS_ZIP_ORDENAMENTO_DEP",
                        "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP", "PLANTAS_CARTOGRAMA_DEP")))
                .andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(true, true, true, false)));
        assertThat(caminhosPendentes(cenario)).containsExactly(
                "plantas.PLANTAS_ZIP_ORDENAMENTO_DEP", "plantas.PLANTAS_ZIP_CONDICIONANTES_DEP", "plantas.PLANTAS_TXT_NOMES_DEP");
    }

    @Test
    void naElaboracaoSemGeoconformFaltaOZipDeCondicionantes() throws Exception {
        Cenario cenario = cenario("ELABORACAO", false, null);
        carregarZipOrdenamento(cenario);
        carregarTxtOrdenamento(cenario);

        assertThat(caminhosPendentes(cenario)).contains("plantas.PLANTAS_ZIP_CONDICIONANTES_DEP").doesNotContain("plantas.zips");
    }

    @Test
    void comGeoconformAsPlantasDeDepositoSaoObtidasDoGeoconformENaoHaPagina() throws Exception {
        Cenario cenario = cenario("ELABORACAO", true, null);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Planta de Ordenamento",
                        "Planta de Condicionantes", "Relatório")))
                .andExpect(jsonPath("$.separadores[1].deposito[0].codigo").value("PLANTA_ORDENAMENTO_DEP"))
                .andExpect(jsonPath("$.separadores[1].deposito[0].obtidoDoGeoconform").value(true))
                .andExpect(jsonPath("$.separadores[2].deposito[0].obtidoDoGeoconform").value(true));
        assertThat(exigePlantasEMetadados(cenario)).isFalse();
        pedirPlantas(cenario).andExpect(jsonPath("$.exigePlantas").value(false));
    }

    @Test
    void naPublicacaoDaAlteracaoSoComPecasEscritasNaoHaPaginaDasPlantas() throws Exception {
        Cenario cenario = cenario("ALTERACAO", false, List.of("PECAS_ESCRITAS"));

        assertThat(exigePlantasEMetadados(cenario)).isFalse();
        pedirPlantas(cenario).andExpect(status().isOk()).andExpect(jsonPath("$.exigePlantas").value(false))
                .andExpect(jsonPath("$.itens.length()").value(0));
        pedirEstrutura(cenario)
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Relatório")));
        assertThat(caminhosPendentes(cenario)).isEmpty();
    }

    @Test
    void naPublicacaoDaAlteracaoSemElementosAfetadosNaoHaPaginaDasPlantas() throws Exception {
        Cenario cenario = cenario("ALTERACAO", false, null);

        assertThat(exigePlantasEMetadados(cenario)).isFalse();
        pedirEstrutura(cenario).andExpect(jsonPath("$.separadores.length()").value(0));
    }

    @Test
    void naPublicacaoDaAlteracaoComPecasGraficasOsItensDaPaginaAplicamSeEOsZipNaoSaoObrigatorios() throws Exception {
        Cenario cenario = cenario("ALTERACAO", false, List.of("PECAS_GRAFICAS"));

        assertThat(exigePlantasEMetadados(cenario)).isTrue();
        pedirPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigePlantas").value(true))
                .andExpect(jsonPath("$.itens[*].codigo").value(contains("PLANTAS_ZIP_ORDENAMENTO_DEP",
                        "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP", "PLANTAS_CARTOGRAMA_DEP")))
                .andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(false, false, true, false)));
        pedirEstrutura(cenario).andExpect(jsonPath("$.separadores[*].separador").value(contains("Planta de Ordenamento")))
                .andExpect(jsonPath("$.separadores[0].deposito.length()").value(0));
    }

    @Test
    void naAlteracaoComUmZipMasSemTxtFaltaOTxt() throws Exception {
        Cenario cenario = cenario("ALTERACAO", false, List.of("PECAS_GRAFICAS"));
        carregarZipOrdenamento(cenario);

        assertThat(caminhosPendentes(cenario)).containsExactly("plantas.PLANTAS_TXT_NOMES_DEP");
    }

    @Test
    void naAlteracaoBastaOZipDeOrdenamentoComOTxtEAsLinhasAssociadas() throws Exception {
        Cenario cenario = cenario("ALTERACAO", false, List.of("PECAS_GRAFICAS"));
        carregarZipOrdenamento(cenario);
        carregarTxtOrdenamento(cenario);
        assertThat(caminhosPendentes(cenario)).containsExactly("plantas.linhas[0]", "plantas.linhas[1]", "plantas.cartograma");

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_B.tif"))
                .andExpect(status().isOk());

        assertThat(caminhosPendentes(cenario)).containsExactly("plantas.cartograma");
    }
}
