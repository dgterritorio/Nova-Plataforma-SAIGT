package com.conkord.saigt.controller.documento;

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
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.MetadadosMotuService;
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
import java.util.LinkedHashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As folhas da página das Plantas ligadas ao Conjunto MOTU, as correções da tabela e a associação pela `linha_txt`. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FolhasDoConjuntoIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9950);
    private static final String HASH = "e".repeat(64);
    private static final String TXT = "Ordenamento; Planta de Ordenamento; 1\n"
            + "Ordenamento; Planta de Ordenamento; 2\n"
            + "Ordenamento; Planta de Ordenamento; 3\n"
            + "Condicionantes; Planta de Condicionantes; 1\n"
            + "Condicionantes; Planta de Condicionantes; 2\n";

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
    private TransactionTemplate transacao;

    @Autowired
    private MetadadosMotuService metadadosMotuService;

    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

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

    private final Map<String, byte[]> objetos = new HashMap<>();

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, Map<String, DocumentoConfig> itens,
                           Map<String, ProcessoDocumento> documentos, UUID ordenamentoId, UUID condicionantesId) {
    }

    @BeforeEach
    void armazenamentoEmMemoria() {
        when(armazenamento.abrir(anyString(), any(), anyLong(), anyLong()))
                .thenAnswer(pedido -> new ByteArrayInputStream(objetos.get(pedido.<String>getArgument(0))));
    }

    /**
     * Um procedimento final sem GeoConform com os dois ZIP de publicação carregados (o Conjunto de cada um), os ZIP de
     * depósito e o TXT da página das Plantas, com 3 folhas de Ordenamento e 2 de Condicionantes por associar.
     */
    private Cenario cenario(boolean comPecasDePublicacao) throws IOException {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("FOL-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("FOL-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Map<String, DocumentoConfig> itens = new LinkedHashMap<>();
        itens.put("ORD_PUB", item(procedimento, "PLANTA_ORDENAMENTO_PUB", "Planta de Ordenamento", "Planta de ordenamento",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 7, PaginaDocumento.DOCUMENTOS));
        itens.put("CON_PUB", item(procedimento, "PLANTA_CONDICIONANTES_PUB", "Planta de Condicionantes", "Planta de condicionantes",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 8, PaginaDocumento.DOCUMENTOS));
        itens.put("ORD", item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", "Planta de Ordenamento", "Planta de ordenamento",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 30, PaginaDocumento.PLANTAS));
        itens.put("CON", item(procedimento, "PLANTAS_ZIP_CONDICIONANTES_DEP", "Planta de Condicionantes", "Planta de condicionantes",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 31, PaginaDocumento.PLANTAS));
        itens.put("TXT", item(procedimento, "PLANTAS_TXT_NOMES_DEP", "Nomes das plantas", "Nomes das plantas conforme o regulamento",
                CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, "[\"txt\"]", 32, PaginaDocumento.PLANTAS));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("512" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-fol.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor);
        dinamica.setComGeoconform(false);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));

        Map<String, ProcessoDocumento> documentos = new LinkedHashMap<>();
        documentos.put("ORD", documento(processo, itens.get("ORD"), autor, "ord.zip", zip("o1.tif", "o2.tif", "o3.tif")));
        documentos.put("CON", documento(processo, itens.get("CON"), autor, "con.zip", zip("c1.tif", "c2.tif")));
        documentos.put("TXT", documento(processo, itens.get("TXT"), autor, "nomes.txt", TXT.getBytes(StandardCharsets.UTF_8)));
        UUID ordenamentoId = null;
        UUID condicionantesId = null;
        if (comPecasDePublicacao) {
            ProcessoDocumento ordPub = documento(processo, itens.get("ORD_PUB"), autor, "ord-pub.zip", zip("v.shp"));
            ProcessoDocumento conPub = documento(processo, itens.get("CON_PUB"), autor, "con-pub.zip", zip("v.shp"));
            documentos.put("ORD_PUB", ordPub);
            documentos.put("CON_PUB", conPub);
            transacao.executeWithoutResult(estado -> {
                metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(ordPub.getId()).orElseThrow());
                metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(conPub.getId()).orElseThrow());
            });
            ordenamentoId = conjuntoDe(ordPub);
            condicionantesId = conjuntoDe(conPub);
        }
        return new Cenario(processo, autor, entidade, itens, documentos, ordenamentoId, condicionantesId);
    }

    private UUID conjuntoDe(ProcessoDocumento documento) {
        return metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documento.getId(), TipoMetadadosMotu.CDG)
                .orElseThrow().getId();
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, String separador, String nome,
                                 CategoriaDocumento categoria, TipoElemento tipoElemento, String extensoes, int ordem,
                                 PaginaDocumento pagina) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, nome, separador, categoria, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, extensoes, ordem);
        item.setPagina(pagina);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private ProcessoDocumento documento(Processo processo, DocumentoConfig item, Utilizador autor, String nome, byte[] conteudo) {
        String caminho = "privado/fol/" + UUID.randomUUID() + "-" + nome;
        objetos.put(caminho, conteudo);
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item, item.getCategoria(),
                nome.substring(nome.lastIndexOf('.') + 1), nome, caminho, HASH, "v1", conteudo.length, autor));
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

    private String base(Cenario cenario) {
        return "/api/v1/processos/" + cenario.processo().getId();
    }

    private ResultActions obterMotu(Cenario cenario) throws Exception {
        return mockMvc.perform(get(base(cenario) + "/metadados-motu").with(jwtDe(cenario)));
    }

    private ResultActions obterPlantas(Cenario cenario) throws Exception {
        return mockMvc.perform(get(base(cenario) + "/plantas").with(jwtDe(cenario)));
    }

    /** Associa as cinco linhas do TXT aos TIFF dos dois ZIP. */
    private ResultActions gravarPlantas(Cenario cenario, int... ordensARetirar) throws Exception {
        String[] tiffs = {"o1.tif", "o2.tif", "o3.tif", "c1.tif", "c2.tif"};
        List<Map<String, Object>> associacoes = new java.util.ArrayList<>();
        outer:
        for (int ordem = 0; ordem < tiffs.length; ordem++) {
            for (int retirada : ordensARetirar) {
                if (retirada == ordem) {
                    continue outer;
                }
            }
            associacoes.add(Map.of("ordem", ordem, "nomeTiff", tiffs[ordem]));
        }
        return mockMvc.perform(put(base(cenario) + "/plantas").with(jwtDe(cenario)).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("associacoes", associacoes))));
    }

    private ResultActions gravarConjunto(Cenario cenario, UUID conjuntoId, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(base(cenario) + "/metadados-motu/" + conjuntoId).with(jwtDe(cenario))
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    private List<Planta> linhas(Cenario cenario) {
        return transacao.execute(estado -> {
            List<Planta> plantas = plantaRepository.findByProcesso_Id(cenario.processo().getId());
            plantas.forEach(planta -> {
                planta.getProcessoDocumento().getDocumentoConfig().getCodigo();
                if (planta.getMetadadosMotu() != null) {
                    planta.getMetadadosMotu().getId();
                }
            });
            return plantas;
        });
    }

    private Planta linha(Cenario cenario, String tiff) {
        return linhas(cenario).stream().filter(planta -> tiff.equals(planta.getNomeTiff())).findFirst().orElseThrow();
    }

    private static Map<String, Object> folha(UUID id, String nome, String numero) {
        Map<String, Object> folha = new LinkedHashMap<>();
        folha.put("id", id.toString());
        folha.put("nomeRegulamento", nome);
        folha.put("numeroFolha", numero);
        return folha;
    }

    private static Map<String, Object> corpoCom(Map<String, Object>... folhas) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("cartografiaTipo", "OUTRO");
        corpo.put("plantas", List.of(folhas));
        return corpo;
    }

    private UUID idDaLinha(Cenario cenario, String tiff) {
        return linha(cenario, tiff).getId();
    }

    @Test
    void depoisDeGravarAPaginaDasPlantasCadaConjuntoTrazAsSuasFolhasELigaAsLinhasDePlanta() throws Exception {
        Cenario cenario = cenario(true);

        gravarPlantas(cenario).andExpect(status().isOk());

        obterMotu(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registos[*].tipo").value(contains("SERIE", "CDG", "CDG")))
                .andExpect(jsonPath("$.registos[1].titulo").value("Planta de ordenamento"))
                .andExpect(jsonPath("$.registos[1].plantas.length()").value(3))
                .andExpect(jsonPath("$.registos[1].plantas[*].numeroFolha").value(contains("1", "2", "3")))
                .andExpect(jsonPath("$.registos[1].plantas[*].nomeTiff").value(contains("o1.tif", "o2.tif", "o3.tif")))
                .andExpect(jsonPath("$.registos[1].plantas[0].nomeRegulamento").value("Planta de Ordenamento"))
                .andExpect(jsonPath("$.registos[2].titulo").value("Planta de condicionantes"))
                .andExpect(jsonPath("$.registos[2].plantas.length()").value(2))
                .andExpect(jsonPath("$.registos[2].plantas[*].nomeTiff").value(contains("c1.tif", "c2.tif")));
        List<Planta> linhas = linhas(cenario);
        assertThat(linhas).hasSize(5);
        assertThat(linhas.stream().filter(planta -> planta.getNomeTiff().startsWith("o")))
                .allSatisfy(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(cenario.ordenamentoId()));
        assertThat(linhas.stream().filter(planta -> planta.getNomeTiff().startsWith("c")))
                .allSatisfy(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(cenario.condicionantesId()));
        assertThat(linhas).extracting(Planta::getLinhaTxt).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
    }

    @Test
    void ligarFolhasAposAPecaDePublicacaoSerCarregadaDepoisDaPaginaDasPlantas() throws Exception {
        Cenario cenario = cenario(false);
        gravarPlantas(cenario).andExpect(status().isOk());
        assertThat(linhas(cenario)).allSatisfy(planta -> assertThat(planta.getMetadadosMotu()).isNull());

        ProcessoDocumento ordPub = documento(cenario.processo(), cenario.itens().get("ORD_PUB"), cenario.autor(), "ord-pub.zip", zip("v.shp"));
        transacao.executeWithoutResult(estado -> metadadosMotuService.registarPecaGrafica(
                processoDocumentoRepository.findById(ordPub.getId()).orElseThrow()));

        UUID conjunto = conjuntoDe(ordPub);
        assertThat(linhas(cenario).stream().filter(planta -> planta.getNomeTiff().startsWith("o")))
                .hasSize(3).allSatisfy(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(conjunto));
        assertThat(linhas(cenario).stream().filter(planta -> planta.getNomeTiff().startsWith("c")))
                .allSatisfy(planta -> assertThat(planta.getMetadadosMotu()).isNull());
    }

    @Test
    void aCorrecaoDoNomeNaTabelaMudaAPlantaEUmaFolhaSemNumeroDeixaOConjuntoIncompleto() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        UUID primeira = idDaLinha(cenario, "o1.tif");
        UUID segunda = idDaLinha(cenario, "o2.tif");
        UUID terceira = idDaLinha(cenario, "o3.tif");
        Map<String, Object> corpo = comTodosOsCampos(corpoCom(folha(primeira, "Uso do Solo corrigido", "1"),
                folha(segunda, "Planta de Ordenamento", "2"), folha(terceira, "Planta de Ordenamento", "3")));

        gravarConjunto(cenario, cenario.ordenamentoId(), corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.plantas[0].nomeRegulamento").value("Uso do Solo corrigido"));
        assertThat(linha(cenario, "o1.tif").getNomeRegulamento()).isEqualTo("Uso do Solo corrigido");

        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(folha(primeira, "Uso do Solo corrigido", ""),
                folha(segunda, "Planta de Ordenamento", "2"), folha(terceira, "Planta de Ordenamento", "3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
        assertThat(linha(cenario, "o1.tif").getNumeroFolha()).isNull();
    }

    @Test
    void umaFolhaDeOutroConjuntoOuDeOutroProcessoDa400SemAlterarNada() throws Exception {
        Cenario cenario = cenario(true);
        Cenario outro = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        gravarPlantas(outro).andExpect(status().isOk());
        UUID deCondicionantes = idDaLinha(cenario, "c1.tif");
        UUID deOutroProcesso = idDaLinha(outro, "o1.tif");

        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(folha(deCondicionantes, "Intruso", "9"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("plantas[0].id")));
        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(folha(deOutroProcesso, "Intruso", "9"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("plantas[0].id")));

        assertThat(linha(cenario, "c1.tif").getNomeRegulamento()).isEqualTo("Planta de Condicionantes");
        assertThat(linha(outro, "o1.tif").getNomeRegulamento()).isEqualTo("Planta de Ordenamento");
        assertThat(metadadosMotuRepository.findById(cenario.ordenamentoId()).orElseThrow().getDados()).isEqualTo("{}");
    }

    @Test
    void umNomeOuUmaFolhaAcimaDoLimiteDa400ComOCampoDaFolha() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());

        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(
                folha(idDaLinha(cenario, "o1.tif"), "n".repeat(501), "1".repeat(21)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("plantas[0].nomeRegulamento", "plantas[0].numeroFolha")));
    }

    @Test
    void aCorrecaoSobreviveAoAbrirEAoVoltarAGravarAPaginaDasPlantasEACorrecaoNaoDaPendenciaDeAssociar() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        UUID primeira = idDaLinha(cenario, "o1.tif");
        UUID segunda = idDaLinha(cenario, "o2.tif");
        UUID terceira = idDaLinha(cenario, "o3.tif");
        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(folha(primeira, "Corrigido", "1A"),
                folha(segunda, "Planta de Ordenamento", "2"), folha(terceira, "Planta de Ordenamento", "3")))).andExpect(status().isOk());

        obterPlantas(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas[0].nomeTiff").value("o1.tif"))
                .andExpect(jsonPath("$.linhas[*].nomeTiff").value(contains("o1.tif", "o2.tif", "o3.tif", "c1.tif", "c2.tif")));
        List<String> pendencias = transacao.execute(estado -> plantasServicePendencias(cenario));
        assertThat(pendencias).doesNotContain("plantas.linhas[0]");

        gravarPlantas(cenario).andExpect(status().isOk());

        Planta mantida = linha(cenario, "o1.tif");
        assertThat(mantida.getId()).isEqualTo(primeira);
        assertThat(mantida.getNomeRegulamento()).isEqualTo("Corrigido");
        assertThat(mantida.getNumeroFolha()).isEqualTo("1A");
        assertThat(mantida.getMetadadosMotu().getId()).isEqualTo(cenario.ordenamentoId());
        assertThat(mantida.getLinhaTxt()).isZero();
    }

    @Test
    void aLinhaDoTxtAssociadaAOutroTiffCriaUmaLinhaNovaComONomeDoTxtELigadaAoConjunto() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        UUID primeira = idDaLinha(cenario, "o1.tif");
        gravarConjunto(cenario, cenario.ordenamentoId(), comTodosOsCampos(corpoCom(folha(primeira, "Corrigido", "1A"))))
                .andExpect(status().isOk());

        mockMvc.perform(put(base(cenario) + "/plantas").with(jwtDe(cenario)).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("associacoes", List.of(Map.of("ordem", 0, "nomeTiff", "o3.tif"))))))
                .andExpect(status().isOk());

        List<Planta> linhas = linhas(cenario);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getId()).isNotEqualTo(primeira);
        assertThat(linhas.get(0).getNomeRegulamento()).isEqualTo("Planta de Ordenamento");
        assertThat(linhas.get(0).getNumeroFolha()).isEqualTo("1");
        assertThat(linhas.get(0).getMetadadosMotu().getId()).isEqualTo(cenario.ordenamentoId());
        obterMotu(cenario).andExpect(jsonPath("$.registos[1].plantas[0].nomeRegulamento").value("Planta de Ordenamento"));
    }

    @Test
    void aoRemoverOTxtAsLinhasDePlantaSaemEAsLinhasDoTxtFicamPorAssociar() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        assertThat(linhas(cenario)).hasSize(5);

        mockMvc.perform(delete(base(cenario) + "/documentos/" + cenario.documentos().get("TXT").getId()).with(jwtDe(cenario)))
                .andExpect(status().isNoContent());

        assertThat(linhas(cenario)).isEmpty();
        obterMotu(cenario)
                .andExpect(jsonPath("$.registos[1].plantas.length()").value(0))
                .andExpect(jsonPath("$.registos[1].completo").value(false));
    }

    @Test
    void aoRemoverOZipDeUmTipoSoSaemAsLinhasDesseZipEOsOutrosFicamLigados() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());

        mockMvc.perform(delete(base(cenario) + "/documentos/" + cenario.documentos().get("CON").getId()).with(jwtDe(cenario)))
                .andExpect(status().isNoContent());

        assertThat(linhas(cenario)).hasSize(3).allSatisfy(planta ->
                assertThat(planta.getMetadadosMotu().getId()).isEqualTo(cenario.ordenamentoId()));
    }

    @Test
    void linhasAnterioresALinhaTxtContinuamAssociadasPeloNomeEPelaFolha() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());
        transacao.executeWithoutResult(estado -> plantaRepository.findByProcesso_Id(cenario.processo().getId())
                .forEach(planta -> planta.setLinhaTxt(null)));

        obterPlantas(cenario)
                .andExpect(jsonPath("$.linhas[*].nomeTiff").value(contains("o1.tif", "o2.tif", "o3.tif", "c1.tif", "c2.tif")));

        gravarPlantas(cenario).andExpect(status().isOk());
        assertThat(linhas(cenario)).hasSize(5).extracting(Planta::getLinhaTxt).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
    }

    @Test
    void aoRemoverAPecaDePublicacaoOConjuntoSaiASerieFicaEAsFolhasDesseTipoFicamSemLigacao() throws Exception {
        Cenario cenario = cenario(true);
        gravarPlantas(cenario).andExpect(status().isOk());

        mockMvc.perform(delete(base(cenario) + "/documentos/" + cenario.documentos().get("ORD_PUB").getId()).with(jwtDe(cenario)))
                .andExpect(status().isNoContent());

        obterMotu(cenario)
                .andExpect(jsonPath("$.registos[*].tipo").value(contains("SERIE", "CDG")))
                .andExpect(jsonPath("$.registos[1].titulo").value("Planta de condicionantes"));
        assertThat(linhas(cenario).stream().filter(planta -> planta.getNomeTiff().startsWith("o")))
                .hasSize(3).allSatisfy(planta -> assertThat(planta.getMetadadosMotu()).isNull());
        assertThat(linhas(cenario).stream().filter(planta -> planta.getNomeTiff().startsWith("c")))
                .allSatisfy(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(cenario.condicionantesId()));
    }

    @Autowired
    private com.conkord.saigt.service.documento.PlantasService plantasService;

    private List<String> plantasServicePendencias(Cenario cenario) {
        return plantasService.pendencias(processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream()
                .map(com.conkord.saigt.exception.ValidacaoException.CampoErro::path).toList();
    }

    /** Os obrigatórios do Conjunto com o tipo `OUTRO`, mais o que o corpo já traz (`plantas`). */
    private static Map<String, Object> comTodosOsCampos(Map<String, Object> corpo) {
        corpo.put("tituloAlternativo", "Título alternativo");
        corpo.put("resumo", "Resumo");
        corpo.put("palavrasChave", "palavras");
        corpo.put("denominadorEscala", "10000");
        corpo.put("sistemaReferencia", "ETRS89");
        corpo.put("dataEdicao", "2024-05-01");
        corpo.put("atualizacaoComplemento", "Nenhuma");
        corpo.put("entidadeProprietaria", "DGT");
        corpo.put("entidadeProdutora", "Município");
        corpo.put("sistemaReferenciaNome", "ETRS89");
        corpo.put("sistemaReferenciaCodigo", "4258");
        corpo.put("exatidaoPosicional", "1 m");
        corpo.put("exatidaoTematica", "95 %");
        return corpo;
    }
}
