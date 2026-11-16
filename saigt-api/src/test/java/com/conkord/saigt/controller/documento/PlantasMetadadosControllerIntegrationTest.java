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
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.PlantaService;
import com.conkord.saigt.service.documento.PlantaService.LinhaDaPagina;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

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
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantasMetadadosControllerIntegrationTest {

    private static final String HASH = "b".repeat(64);
    private static final String TXT = "Ordenamento; Planta de Ordenamento – Uso do Solo; A\n"
            + "Ordenamento; Planta de Ordenamento – Uso do Solo; B\n"
            + "Condicionantes; Planta de Condicionantes – RAN; A\n";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9300);

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
    private PlantaRepository plantaRepository;

    @Autowired
    private PlantaService plantaService;

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
        when(armazenamento.abrir(anyString(), any(), anyLong(), anyLong())).thenAnswer(pedido -> new ByteArrayInputStream(objetos.get(pedido.<String>getArgument(0))));
    }

    private Cenario cenario(Boolean comGeoconform) {
        return cenario(comGeoconform, "ELABORACAO", false, null);
    }

    /**
     * Um procedimento final: além dos itens da página das Plantas, a lista de documentos tem o item de depósito da
     * Planta de Ordenamento, com o mesmo nome do ZIP. Fora da Elaboração, o procedimento exige elementos afetados.
     */
    private Cenario procedimentoFinal(Boolean comGeoconform, String tipoDinamica, String... elementosAfetados) {
        return cenario(comGeoconform, tipoDinamica, true, elementosAfetados.length == 0 ? null : List.of(elementosAfetados));
    }

    private Cenario cenario(Boolean comGeoconform, String codigoDinamica, boolean comPlantaDeposito,
                            List<String> elementosAfetados) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PLT-" + n, "Plano " + n, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.findAll().stream().filter(tipo -> codigoDinamica.equals(tipo.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoDinamica, codigoDinamica)));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PLT-DEC-" + n, "Decisão de início"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Map<String, DocumentoConfig> itens = new HashMap<>();
        itens.put("TEXTO", item(procedimento, "TEXTO", "Texto do Ato", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                "[\"pdf\"]", true, 1, PaginaDocumento.DOCUMENTOS));
        itens.put("ORDENAMENTO", item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", "Planta de Ordenamento", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 101, PaginaDocumento.PLANTAS));
        itens.put("CONDICIONANTES", item(procedimento, "PLANTAS_ZIP_CONDICIONANTES_DEP", "Planta de Condicionantes",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 102, PaginaDocumento.PLANTAS));
        itens.put("TXT", item(procedimento, "PLANTAS_TXT_NOMES_DEP", "Nomes das plantas", CategoriaDocumento.DEPOSITO,
                TipoElemento.OUTROS, "[\"txt\"]", false, 103, PaginaDocumento.PLANTAS));
        itens.put("CARTOGRAMA", item(procedimento, "PLANTAS_CARTOGRAMA_DEP", "Cartograma das folhas", CategoriaDocumento.DEPOSITO,
                TipoElemento.OUTROS, "[\"png\"]", false, 104, PaginaDocumento.PLANTAS));
        if (comPlantaDeposito) {
            itens.put("PLANTA_DEP", item(procedimento, "PLANTA_ORDENAMENTO_DEP", "Planta de Ordenamento", CategoriaDocumento.DEPOSITO,
                    TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 2, PaginaDocumento.DOCUMENTOS));
        }

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("508" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-plt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Dinâmica do PDM", autor);
        dinamica.setComGeoconform(comGeoconform);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setElementosAfetados(elementosAfetados);
        processo = processoRepository.saveAndFlush(processo);
        return new Cenario(processo, autor, entidade, itens);
    }

    private ResultActions pedirEstrutura(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental").with(jwtDe(cenario)));
    }

    private boolean exigePlantasEMetadados(Cenario cenario) {
        return Boolean.TRUE.equals(transacao.execute(estado -> completudeDocumentalService.exigePlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow())));
    }

    private List<String> pendenciasDasPlantas(Cenario cenario) {
        return pendencias(cenario).stream().filter(campo -> campo.startsWith("plantas.")).toList();
    }

    @Test
    void procedimentoFinalSemGeoconformTiraDaListaAPlantaDeDepositoComONomeDoZipEMostraAPaginaDasPlantas() throws Exception {
        Cenario cenario = procedimentoFinal(false, "ELABORACAO");

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato")));
        assertThat(exigePlantasEMetadados(cenario)).isTrue();
        pedirPlantas(cenario)
                .andExpect(jsonPath("$.exigePlantas").value(true))
                .andExpect(jsonPath("$.itens[*].codigo").value(contains("PLANTAS_ZIP_ORDENAMENTO_DEP",
                        "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP", "PLANTAS_CARTOGRAMA_DEP")))
                .andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(true, true, true, false)));

        carregar(cenario, "ORDENAMENTO", "ordenamento.zip", zip("1199_Ord_A.tif"));
        carregar(cenario, "TXT", "nomes.txt", "Ordenamento; Planta de Ordenamento – Uso do Solo; A\n".getBytes(StandardCharsets.UTF_8));
        assertThat(pendenciasDasPlantas(cenario)).contains("plantas.PLANTAS_ZIP_CONDICIONANTES_DEP");
    }

    @Test
    void procedimentoFinalComGeoconformMantemAPlantaDeDepositoNaListaESemPaginaDasPlantas() throws Exception {
        Cenario cenario = procedimentoFinal(true, "ELABORACAO");

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Planta de Ordenamento")))
                .andExpect(jsonPath("$.separadores[1].deposito[0].codigo").value("PLANTA_ORDENAMENTO_DEP"));
        assertThat(exigePlantasEMetadados(cenario)).isFalse();
        pedirPlantas(cenario).andExpect(jsonPath("$.exigePlantas").value(false));
    }

    @Test
    void naPublicacaoDaAlteracaoSoComPecasEscritasNaoHaPaginaDasPlantasNemPendenciasDelas() throws Exception {
        Cenario cenario = procedimentoFinal(false, "ALTERACAO", "PECAS_ESCRITAS");

        assertThat(exigePlantasEMetadados(cenario)).isFalse();
        pedirPlantas(cenario).andExpect(jsonPath("$.exigePlantas").value(false));
        assertThat(pendencias(cenario)).isEmpty();
    }

    @Test
    void naAlteracaoComPecasGraficasBastaUmaPlantaEOTxtECartogramaEntramSemOutros() throws Exception {
        Cenario cenario = procedimentoFinal(false, "ALTERACAO", "PECAS_GRAFICAS");

        assertThat(exigePlantasEMetadados(cenario)).isTrue();
        pedirEstrutura(cenario).andExpect(jsonPath("$.separadores.length()").value(0));
        pedirPlantas(cenario)
                .andExpect(jsonPath("$.itens[*].codigo").value(contains("PLANTAS_ZIP_ORDENAMENTO_DEP",
                        "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP", "PLANTAS_CARTOGRAMA_DEP")))
                .andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(false, false, true, false)));
        assertThat(pendenciasDasPlantas(cenario)).containsExactly("plantas.zips", "plantas.PLANTAS_TXT_NOMES_DEP");

        carregar(cenario, "ORDENAMENTO", "ordenamento.zip", zip("1199_Ord_A.tif"));
        assertThat(pendenciasDasPlantas(cenario)).containsExactly("plantas.PLANTAS_TXT_NOMES_DEP");

        carregar(cenario, "TXT", "nomes.txt", "Ordenamento; Planta de Ordenamento – Uso do Solo; A\n".getBytes(StandardCharsets.UTF_8));
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif")).andExpect(status().isOk());
        assertThat(pendenciasDasPlantas(cenario)).isEmpty();
    }

    @Test
    void foraDaElaboracaoAPaginaDasPlantasSoApareceComPecasGraficas() throws Exception {
        assertThat(exigePlantasEMetadados(cenario(false, "REVISAO", false, List.of("PECAS_ESCRITAS")))).isFalse();
        assertThat(exigePlantasEMetadados(cenario(false, "REVISAO", false, List.of("PECAS_GRAFICAS")))).isTrue();

        Cenario revisao = cenario(false, "REVISAO", false, List.of("PECAS_GRAFICAS"));
        pedirPlantas(revisao).andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(true, true, true, false)));
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, String separador,
                                 CategoriaDocumento categoria, TipoElemento tipoElemento, String extensoes, boolean obrigatorio,
                                 int ordem, PaginaDocumento pagina) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, separador, separador, categoria, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, extensoes, ordem);
        item.setObrigatorio(obrigatorio);
        item.setPagina(pagina);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private void carregar(Cenario cenario, String item, String nome, byte[] conteudo) {
        String caminho = "privado/plt/" + UUID.randomUUID() + "-" + nome;
        objetos.put(caminho, conteudo);
        DocumentoConfig config = cenario.itens().get(item);
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), config, config.getCategoria(),
                nome.substring(nome.lastIndexOf('.') + 1), nome, caminho, HASH, "v1", conteudo.length, cenario.autor()));
    }

    private void carregarPlantas(Cenario cenario) throws IOException {
        carregar(cenario, "ORDENAMENTO", "ordenamento.zip", zip("1199_Ord_A.tif", "1199_Ord_A.tfw", "1199_Ord_B.tif"));
        carregar(cenario, "CONDICIONANTES", "condicionantes.zip", zip("1199_Cond_A.tif"));
        carregar(cenario, "TXT", "nomes.txt", TXT.getBytes(StandardCharsets.UTF_8));
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

    private ResultActions gravarPlantas(Cenario cenario, AssociacaoPlanta... associacoes) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/plantas").with(jwtDe(cenario))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new GravarPlantasRequest(List.of(associacoes)))));
    }

    private List<String> pendencias(Cenario cenario) {
        return transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream().map(CampoErro::path).toList());
    }

    private List<String> plantasGravadas(Cenario cenario) {
        return transacao.execute(estado -> plantaRepository.findByProcesso_Id(cenario.processo().getId()).stream()
                .map(planta -> planta.getNomeRegulamento() + "|" + planta.getNumeroFolha() + "|" + planta.getNomeTiff() + "|"
                        + planta.getNomeZip() + "|" + planta.getProcessoDocumento().getNomeFicheiro())
                .sorted().toList());
    }

    private ProcessoDocumento documentoCarregado(Cenario cenario, String nome) {
        return processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()).stream()
                .filter(documento -> nome.equals(documento.getNomeFicheiro())).findFirst().orElseThrow();
    }

    private static final String LINHAS_GRAVADAS_PLANTA = "Planta de Ordenamento – Uso do Solo|A|1199_Ord_A.tif|ordenamento.zip|ordenamento.zip";

    @Test
    void deveDevolverAsLinhasDoTxtComOsTiffDoZipDoMesmoTipo() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);

        pedirPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigePlantas").value(true))
                .andExpect(jsonPath("$.exigeCartograma").value(true))
                .andExpect(jsonPath("$.itens[*].codigo").value(contains("PLANTAS_ZIP_ORDENAMENTO_DEP",
                        "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP", "PLANTAS_CARTOGRAMA_DEP")))
                .andExpect(jsonPath("$.itens[*].obrigatorio").value(contains(true, true, true, false)))
                .andExpect(jsonPath("$.itens[0].ficheiro.nomeFicheiro").value("ordenamento.zip"))
                .andExpect(jsonPath("$.linhas.length()").value(3))
                .andExpect(jsonPath("$.linhas[0].nome").value("Planta de Ordenamento – Uso do Solo"))
                .andExpect(jsonPath("$.linhas[0].folha").value("A"))
                .andExpect(jsonPath("$.linhas[0].opcoes").value(contains("1199_Ord_A.tif", "1199_Ord_B.tif")))
                .andExpect(jsonPath("$.linhas[2].opcoes").value(contains("1199_Cond_A.tif")))
                .andExpect(jsonPath("$.errosTxt.length()").value(0));
    }

    @Test
    void comGeoconformNaoHaPaginaDasPlantasNemOsSeusItensNosDocumentos() throws Exception {
        Cenario cenario = cenario(true);

        pedirPlantas(cenario).andExpect(status().isOk()).andExpect(jsonPath("$.exigePlantas").value(false));
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(1))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(contains("TEXTO")));
        assertThat(pendencias(cenario)).isEmpty();
    }

    @Test
    void semGeoconformOsItensDasPlantasNaoAparecemNosSeparadoresEOsFicheirosFaltamNaPaginaDasPlantas() throws Exception {
        Cenario cenario = cenario(false);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental").with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(1))
                .andExpect(jsonPath("$.separadores[0].separador").value("Texto do Ato"))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(contains("TEXTO")));
        assertThat(pendencias(cenario)).filteredOn(campo -> campo.startsWith("plantas.")).containsExactly(
                "plantas.PLANTAS_ZIP_ORDENAMENTO_DEP", "plantas.PLANTAS_ZIP_CONDICIONANTES_DEP", "plantas.PLANTAS_TXT_NOMES_DEP");
    }

    @Test
    void deveGravarAAssociacaoEPedirOCartogramaQuandoHaMaisDeUmaFolha() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);
        assertThat(pendencias(cenario)).contains("plantas.linhas[0]", "plantas.linhas[1]", "plantas.linhas[2]", "plantas.cartograma");

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_B.tif"),
                new AssociacaoPlanta(2, "1199_Cond_A.tif"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas[1].nomeTiff").value("1199_Ord_B.tif"));
        assertThat(pendencias(cenario)).filteredOn(campo -> campo.startsWith("plantas.")).containsExactly("plantas.cartograma");
        assertThat(plantasGravadas(cenario)).containsExactly(
                "Planta de Condicionantes – RAN|A|1199_Cond_A.tif|condicionantes.zip|condicionantes.zip",
                LINHAS_GRAVADAS_PLANTA,
                "Planta de Ordenamento – Uso do Solo|B|1199_Ord_B.tif|ordenamento.zip|ordenamento.zip");

        carregar(cenario, "CARTOGRAMA", "cartograma.png", new byte[] {1, 2});
        assertThat(pendencias(cenario)).filteredOn(campo -> campo.startsWith("plantas.")).isEmpty();
    }

    private ResultActions pedirPdf(Cenario cenario, RequestPostProcessor quem) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/plantas/cartograma.pdf").with(quem));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    @Test
    void deveGerarOPdfDoCartogramaComAsPlantasEOsTiffDasFolhas() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_B.tif"),
                new AssociacaoPlanta(2, "1199_Cond_A.tif")).andExpect(status().isOk());
        carregar(cenario, "CARTOGRAMA", "cartograma.png", png());

        pedirPlantas(cenario).andExpect(jsonPath("$.cartogramaPdfDisponivel").value(true));
        byte[] pdf = pedirPdf(cenario, jwtDe(cenario))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documento).lines().map(String::strip).filter(linha -> !linha.isEmpty()))
                    .containsExactly("Planta de Ordenamento – Uso do Solo", "1199_Ord_A", "1199_Ord_B",
                            "Planta de Condicionantes – RAN", "1199_Cond_A");
        }
    }

    @Test
    void semCartogramaOuComLinhasPorAssociarNaoHaPdf() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif")).andExpect(status().isOk());

        pedirPlantas(cenario).andExpect(jsonPath("$.cartogramaPdfDisponivel").value(false));
        pedirPdf(cenario, jwtDe(cenario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("plantas.cartograma", "plantas.linhas[1]", "plantas.linhas[2]")));

        carregar(cenario, "CARTOGRAMA", "cartograma.png", png());
        pedirPlantas(cenario).andExpect(jsonPath("$.cartogramaPdfDisponivel").value(false));
        pedirPdf(cenario, jwtDe(cenario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("plantas.linhas[1]", "plantas.linhas[2]")));
    }

    @Test
    void oPdfDeUmProcessoDeOutraEntidadeTemAMesmaRespostaQueAsPlantas() throws Exception {
        Cenario cenario = cenario(false);
        Cenario outra = cenario(false);
        carregarPlantas(cenario);

        int plantas = mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/plantas").with(jwtDe(outra)))
                .andReturn().getResponse().getStatus();
        pedirPdf(cenario, jwtDe(outra)).andExpect(status().is(plantas));
        assertThat(plantas).isEqualTo(404);
    }

    @Test
    void deveGravarUmaLinhaPlantaPorAssociacaoESubstituirAsAnterioresAoGravarDeNovo() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_B.tif"),
                new AssociacaoPlanta(2, "1199_Cond_A.tif")).andExpect(status().isOk());
        assertThat(plantasGravadas(cenario)).hasSize(3);

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_B.tif")).andExpect(status().isOk());

        assertThat(plantasGravadas(cenario)).containsExactly(
                "Planta de Ordenamento – Uso do Solo|A|1199_Ord_B.tif|ordenamento.zip|ordenamento.zip");
    }

    @Test
    void aoSubstituirOuRemoverOZipSaemAsSuasLinhasEAsDoTxtDesseTipoFicamPorAssociar() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_B.tif"),
                new AssociacaoPlanta(2, "1199_Cond_A.tif")).andExpect(status().isOk());

        UUID ordenamento = documentoCarregado(cenario, "ordenamento.zip").getId();
        transacao.executeWithoutResult(estado -> plantaService.atualizarAposSubstituicao(
                processoDocumentoRepository.findById(ordenamento).orElseThrow()));

        assertThat(plantasGravadas(cenario)).containsExactly(
                "Planta de Condicionantes – RAN|A|1199_Cond_A.tif|condicionantes.zip|condicionantes.zip");
        assertThat(pendencias(cenario)).contains("plantas.linhas[0]", "plantas.linhas[1]").doesNotContain("plantas.linhas[2]");

        UUID condicionantes = documentoCarregado(cenario, "condicionantes.zip").getId();
        transacao.executeWithoutResult(estado -> {
            plantaService.removerDoDocumento(condicionantes);
            processoDocumentoRepository.deleteById(condicionantes);
        });

        assertThat(plantasGravadas(cenario)).isEmpty();
    }

    @Test
    void oCarregamentoDosZipDaPaginaDasPlantasNaoCriaLinhasPlantaMasOutraPecaGraficaCria() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);
        assertThat(plantasGravadas(cenario)).isEmpty();

        UUID ordenamento = documentoCarregado(cenario, "ordenamento.zip").getId();
        transacao.executeWithoutResult(estado -> plantaService.registarPecaGrafica(
                processoDocumentoRepository.findById(ordenamento).orElseThrow()));
        assertThat(plantasGravadas(cenario)).isEmpty();

        DocumentoConfig foraDaPagina = item(cenario.processo().getInstrumentoProcedimentoConfig(), "AREA", "Planta com área",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", false, 105, PaginaDocumento.DOCUMENTOS);
        ProcessoDocumento area = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), foraDaPagina,
                CategoriaDocumento.DEPOSITO, "zip", "area.zip", "privado/plt/area", HASH, "v1", 1, cenario.autor()));
        transacao.executeWithoutResult(estado -> plantaService.registarPecaGrafica(area));

        assertThat(plantasGravadas(cenario)).containsExactly("null|null|null|area.zip|area.zip");
    }

    @Test
    void deveRecusarUmTiffDeOutroZipOuRepetido() throws Exception {
        Cenario cenario = cenario(false);
        carregarPlantas(cenario);

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Cond_A.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("associacoes[0].nomeTiff"));
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(1, "1199_Ord_A.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value("Este TIFF já está associado a outra planta."));
        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Ord_A.tif"), new AssociacaoPlanta(0, "1199_Ord_B.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value("Esta linha já tem um TIFF associado."));
        gravarPlantas(cenario, new AssociacaoPlanta(7, "1199_Ord_A.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("associacoes[0].ordem"));
    }

    @Test
    void deveGravarAFichaDeMetadadosComoRascunhoEExigirOsObrigatoriosNaSubmissao() throws Exception {
        Cenario cenario = cenario(false);
        String url = "/api/v1/processos/" + cenario.processo().getId() + "/metadados";

        mockMvc.perform(get(url).with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeMetadados").value(true))
                .andExpect(jsonPath("$.titulo").value(cenario.processo().getDinamica().getInstrumento().getDesignacao()))
                .andExpect(jsonPath("$.responsavel.funcao").value("Produtor"))
                .andExpect(jsonPath("$.responsavel.pais").value("Portugal"));
        assertThat(pendencias(cenario)).contains("metadados.palavrasChave", "metadados.exatidaoTematica");

        String ficha = "{\"palavrasChave\":\"ordenamento\",\"resolucaoEspacialEscala\":\"10000\",\"tituloAlternativo\":\"PDM\","
                + "\"resumo\":\"Plano diretor\",\"sistemaReferencia\":\"PT-TM06/ETRS89\",\"cartografiaTipo\":\"Cartografia Homologada\","
                + "\"entidadeProprietaria\":\"Câmara\",\"dataEdicao\":\"2026-01-15\",\"entidadeProdutora\":\"Câmara\","
                + "\"atualizacaoComplemento\":\"Não\",\"sistemaReferenciaNome\":\"PT-TM06/ETRS89\",\"exatidaoPosicional\":\"1 m\","
                + "\"exatidaoTematica\":\"95%\"}";
        mockMvc.perform(put(url).with(jwtDe(cenario)).contentType("application/json").content(ficha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.dataEdicao").value("2026-01-15"));

        mockMvc.perform(get(url).with(jwtDe(cenario)))
                .andExpect(jsonPath("$.dados.resumo").value("Plano diretor"));
        assertThat(pendencias(cenario)).filteredOn(campo -> campo.startsWith("metadados.")).isEmpty();
    }

    /** Linhas do TXT sem TIFF associado: o detalhe do processo leva-as para a página das Plantas. */
    @Test
    void oDetalheDoProcessoDizAsLinhasSemTiffNaPaginaDasPlantas() throws Exception {
        Cenario cenario = cenario(false);
        comFormulario(cenario);
        carregarPlantas(cenario);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDe(cenario)))
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.pagina == 'PLANTAS')].campo")
                        .value(hasItems("plantas.linhas[0]", "plantas.linhas[1]", "plantas.linhas[2]")))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.campo == 'plantas.linhas[2]')].erro")
                        .value(contains("Associe um TIFF a «Planta de Condicionantes – RAN – folha A».")));
    }

    /** Só um processo editável tem pendências: o Procedimento precisa de um formulário. */
    private void comFormulario(Cenario cenario) {
        TipoProcedimento comFormulario = tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> "CONSTITUICAO_CC".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
        transacao.executeWithoutResult(estado -> {
            InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository
                    .findById(cenario.processo().getInstrumentoProcedimentoConfig().getId()).orElseThrow();
            ReflectionTestUtils.setField(procedimento, "tipoProcedimento", comFormulario);
            procedimentoConfigRepository.saveAndFlush(procedimento);
        });
    }

    @Test
    void semZipDoTipoALinhaNaoTemOpcoesNemAceitaOTiffDeOutraPlantaEFicaPorAssociar() throws Exception {
        Cenario cenario = cenario(false);
        comFormulario(cenario);
        carregar(cenario, "CONDICIONANTES", "condicionantes.zip", zip("1199_Cond_A.tif"));
        carregar(cenario, "TXT", "nomes.txt", TXT.getBytes(StandardCharsets.UTF_8));

        pedirPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas.length()").value(3))
                .andExpect(jsonPath("$.linhas[0].opcoes").isEmpty())
                .andExpect(jsonPath("$.linhas[1].opcoes").isEmpty())
                .andExpect(jsonPath("$.linhas[2].opcoes").value(contains("1199_Cond_A.tif")));

        gravarPlantas(cenario, new AssociacaoPlanta(0, "1199_Cond_A.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("associacoes[0].nomeTiff"))
                .andExpect(jsonPath("$.campos[0].erro").value("O TIFF não está no ZIP desta planta."));
        assertThat(plantasGravadas(cenario)).isEmpty();

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDe(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.pagina == 'PLANTAS')].campo")
                        .value(hasItems("plantas.linhas[0]", "plantas.linhas[1]")));

        carregar(cenario, "ORDENAMENTO", "ordenamento.zip", zip("1199_Ord_A.tif", "1199_Ord_B.tif"));
        pedirPlantas(cenario)
                .andExpect(jsonPath("$.linhas[0].opcoes").value(contains("1199_Ord_A.tif", "1199_Ord_B.tif")))
                .andExpect(jsonPath("$.linhas[1].opcoes").value(contains("1199_Ord_A.tif", "1199_Ord_B.tif")))
                .andExpect(jsonPath("$.linhas[2].opcoes").value(contains("1199_Cond_A.tif")));
    }

    /** Gravada antes de as opções se limitarem ao ZIP do tipo, quando sem ZIP do tipo valiam todos. */
    @Test
    void aAssociacaoAntigaAUmTiffDeOutroTipoNaoContaEALinhaFicaPorAssociar() throws Exception {
        Cenario cenario = cenario(false);
        carregar(cenario, "CONDICIONANTES", "condicionantes.zip", zip("1199_Cond_A.tif"));
        carregar(cenario, "TXT", "nomes.txt", TXT.getBytes(StandardCharsets.UTF_8));
        UUID condicionantes = documentoCarregado(cenario, "condicionantes.zip").getId();
        transacao.executeWithoutResult(estado -> plantaService.substituirDaPaginaDasPlantas(
                processoRepository.findById(cenario.processo().getId()).orElseThrow(),
                List.of(new LinhaDaPagina(processoDocumentoRepository.findById(condicionantes).orElseThrow(), 0,
                        "Planta de Ordenamento – Uso do Solo", "A", "1199_Cond_A.tif"))));
        assertThat(plantasGravadas(cenario)).containsExactly(
                "Planta de Ordenamento – Uso do Solo|A|1199_Cond_A.tif|condicionantes.zip|condicionantes.zip");

        pedirPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas[0].nomeTiff").doesNotExist())
                .andExpect(jsonPath("$.linhas[0].opcoes").isEmpty())
                .andExpect(jsonPath("$.linhas[2].nomeTiff").doesNotExist());
        assertThat(pendencias(cenario)).contains("plantas.linhas[0]", "plantas.linhas[1]", "plantas.linhas[2]");
    }
}
