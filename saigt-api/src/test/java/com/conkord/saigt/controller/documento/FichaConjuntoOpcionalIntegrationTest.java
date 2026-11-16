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
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fichas de Conjunto opcionais: criar, ligar a um ZIP e remover (API-DOC-019, API-DOC-011). */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FichaConjuntoOpcionalIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(19900);
    private static final String HASH = "d".repeat(64);

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
    private CompletudeDocumentalService completudeDocumentalService;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, Map<String, DocumentoConfig> itens,
                           UUID serieId, UUID ordenamentoId, UUID condicionantesId) {
    }

    /**
     * Um procedimento final sem GeoConform com as peças de publicação de ordenamento e de condicionantes (o ZIP de
     * condicionantes carrega-se primeiro, para provar que a ordem é a dos itens) e os ZIP de depósito da página das
     * Plantas com o mesmo nome.
     */
    private Cenario cenario() {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("CDG-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CDG-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Map<String, DocumentoConfig> itens = new LinkedHashMap<>();
        itens.put("ORD_PUB", item(procedimento, "PLANTA_ORDENAMENTO_PUB", "Planta de ordenamento", CategoriaDocumento.PUBLICACAO, 7,
                PaginaDocumento.DOCUMENTOS));
        itens.put("CON_PUB", item(procedimento, "PLANTA_CONDICIONANTES_PUB", "Planta de condicionantes", CategoriaDocumento.PUBLICACAO, 8,
                PaginaDocumento.DOCUMENTOS));
        itens.put("ORD_DEP", item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", "Planta de ordenamento", CategoriaDocumento.DEPOSITO, 30,
                PaginaDocumento.PLANTAS));
        itens.put("CON_DEP", item(procedimento, "PLANTAS_ZIP_CONDICIONANTES_DEP", "Planta de condicionantes", CategoriaDocumento.DEPOSITO, 31,
                PaginaDocumento.PLANTAS));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("511" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-cdg.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor);
        dinamica.setComGeoconform(false);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));

        ProcessoDocumento zipCondicionantes = documento(processo, itens.get("CON_PUB"), autor, "condicionantes-pub.zip");
        ProcessoDocumento zipOrdenamento = documento(processo, itens.get("ORD_PUB"), autor, "ordenamento-pub.zip");
        transacao.executeWithoutResult(estado -> {
            metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(zipCondicionantes.getId()).orElseThrow());
            metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(zipOrdenamento.getId()).orElseThrow());
        });
        UUID serieId = metadadosMotuRepository.findByProcesso_IdAndTipo(processo.getId(), TipoMetadadosMotu.SERIE).orElseThrow().getId();
        UUID ordenamentoId = metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(zipOrdenamento.getId(),
                TipoMetadadosMotu.CDG).orElseThrow().getId();
        UUID condicionantesId = metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(zipCondicionantes.getId(),
                TipoMetadadosMotu.CDG).orElseThrow().getId();
        return new Cenario(processo, autor, entidade, itens, serieId, ordenamentoId, condicionantesId);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, String nome,
                                 CategoriaDocumento categoria, int ordem, PaginaDocumento pagina) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, nome, nome, categoria, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", ordem);
        item.setPagina(pagina);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private ProcessoDocumento documento(Processo processo, DocumentoConfig item, Utilizador autor, String nome) {
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item, item.getCategoria(), "zip", nome,
                "privado/cdg/" + UUID.randomUUID() + "-" + nome, HASH, "v1", 10, autor));
    }

    private static RequestPostProcessor jwtDe(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false)));
    }

    private String url(Cenario cenario) {
        return "/api/v1/processos/" + cenario.processo().getId() + "/metadados-motu";
    }

    private ResultActions obter(Cenario cenario) throws Exception {
        return mockMvc.perform(get(url(cenario)).with(jwtDe(cenario)));
    }

    private ResultActions criar(Cenario cenario, boolean reproduzirUltima) throws Exception {
        return mockMvc.perform(post(url(cenario)).with(jwtDe(cenario)).contentType("application/json")
                .content("{\"reproduzirUltima\":" + reproduzirUltima + "}"));
    }

    private ResultActions gravar(Cenario cenario, UUID motuId, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(url(cenario) + "/" + motuId).with(jwtDe(cenario)).contentType("application/json")
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions remover(Cenario cenario, UUID motuId) throws Exception {
        return mockMvc.perform(delete(url(cenario) + "/" + motuId).with(jwtDe(cenario)));
    }

    private UUID idDoRegisto(String json) throws Exception {
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID criarOpcional(Cenario cenario) throws Exception {
        return idDoRegisto(criar(cenario, false).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> comZip(UUID processoDocumentoId) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("resumo", "Resumo da opcional");
        corpo.put("processoDocumentoId", processoDocumentoId == null ? null : processoDocumentoId.toString());
        return corpo;
    }

    private UUID zipSemFicha(Cenario cenario, String item) {
        return documento(cenario.processo(), cenario.itens().get(item), cenario.autor(), item + ".zip").getId();
    }

    private UUID zipComFicha(Cenario cenario) {
        return transacao.execute(estado -> metadadosMotuRepository.findById(cenario.ordenamentoId()).orElseThrow()
                .getProcessoDocumento().getId());
    }

    private void colocarEmEstado(Cenario cenario, EstadoProcesso estado) {
        Processo atual = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        atual.setEstado(estado);
        processoRepository.saveAndFlush(atual);
    }

    private void gravarDadosDoConjunto(Cenario cenario) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tituloAlternativo", "Planta de Ordenamento do PDM");
        corpo.put("resumo", "Uso do solo");
        corpo.put("cartografiaTipo", "OUTRO");
        corpo.put("exatidaoPosicional", "1 m");
        gravar(cenario, cenario.ordenamentoId(), corpo).andExpect(status().isOk());
    }

    @Test
    void a1_deveCriarUmaFichaOpcionalSemZipIncompletaEListarOpcionalNoGet() throws Exception {
        Cenario cenario = cenario();

        String resposta = criar(cenario, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CDG"))
                .andExpect(jsonPath("$.opcional").value(true))
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.zip").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = idDoRegisto(resposta);

        obter(cenario).andExpect(jsonPath("$.registos.length()").value(4))
                .andExpect(jsonPath("$.registos[?(@.id == '" + id + "')].opcional").value(contains(true)))
                .andExpect(jsonPath("$.registos[?(@.id == '" + id + "')].completo").value(contains(false)));
        assertThat(metadadosMotuRepository.findById(id).orElseThrow().getProcessoDocumento()).isNull();
    }

    @Test
    void a2_comReproduzirUltimaCopiaOsDadosDaUltimaFichaSemOTituloAlternativo() throws Exception {
        Cenario cenario = cenario();
        gravarDadosDoConjunto(cenario);
        UUID anterior = criarOpcional(cenario);
        gravar(cenario, anterior, Map.of("tituloAlternativo", "Título da opcional", "resumo", "Resumo da opcional"))
                .andExpect(status().isOk());

        criar(cenario, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opcional").value(true))
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.dados.tituloAlternativo").doesNotExist())
                .andExpect(jsonPath("$.dados.resumo").value("Resumo da opcional"))
                .andExpect(jsonPath("$.zip").doesNotExist());
    }

    @Test
    void a2_semFichaAnteriorAReproducaoCriaUmaFichaVazia() throws Exception {
        Cenario cenario = cenario();
        transacao.executeWithoutResult(estado -> metadadosMotuRepository.deleteAll(
                metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId()).stream()
                        .filter(ficha -> ficha.getTipo() == TipoMetadadosMotu.CDG).toList()));

        criar(cenario, true).andExpect(status().isCreated()).andExpect(jsonPath("$.dados.resumo").doesNotExist());
    }

    @Test
    void a3_deveRecusarSemFichaMotuAplicavel() throws Exception {
        Cenario cenario = cenario();
        transacao.executeWithoutResult(estado -> {
            cenario.itens().values().forEach(item -> {
                DocumentoConfig gerido = documentoConfigRepository.findById(item.getId()).orElseThrow();
                ReflectionTestUtils.setField(gerido, "ativo", false);
                documentoConfigRepository.save(gerido);
            });
        });

        criar(cenario, false).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MOTU_NAO_APLICAVEL"));
    }

    @Test
    void deveRecusarCriarForaDeEdicaoENaoSeOAutor() throws Exception {
        Cenario cenario = cenario();
        colocarEmEstado(cenario, EstadoProcesso.ENVIADO_DGT);

        criar(cenario, false).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
    }

    @Test
    void b1_deveLigarUmaOpcionalAUmZipSemFichaEOGetMostraOZip() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        UUID zip = zipSemFicha(cenario, "ORD_DEP");

        gravar(cenario, opcional, comZip(zip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zip.id").value(zip.toString()))
                .andExpect(jsonPath("$.zip.nome").value("ORD_DEP.zip"));

        obter(cenario).andExpect(jsonPath("$.registos[?(@.id == '" + opcional + "')].zip.nome").value(contains("ORD_DEP.zip")));
    }

    @Test
    void umaOpcionalSemZipContinuaPermitidaEPodeSerDesligada() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        gravar(cenario, opcional, Map.of("resumo", "sem zip")).andExpect(status().isOk()).andExpect(jsonPath("$.zip").doesNotExist());

        gravar(cenario, opcional, comZip(zipSemFicha(cenario, "ORD_DEP"))).andExpect(jsonPath("$.zip.id").isNotEmpty());
        gravar(cenario, opcional, Map.of("resumo", "só dados")).andExpect(jsonPath("$.zip.id").isNotEmpty());
        gravar(cenario, opcional, comZip(null)).andExpect(status().isOk()).andExpect(jsonPath("$.zip").doesNotExist());
    }

    @Test
    void b2_deveRecusarLigarAUmZipQueJaTemFichaENadaMuda() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        UUID zipComFicha = zipComFicha(cenario);

        gravar(cenario, opcional, comZip(zipComFicha))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_PECA_COM_FICHA"));

        assertThat(metadadosMotuRepository.findById(opcional).orElseThrow().getProcessoDocumento()).isNull();
        UUID livre = zipSemFicha(cenario, "ORD_DEP");
        gravar(cenario, opcional, comZip(livre)).andExpect(status().isOk());
        UUID outra = criarOpcional(cenario);
        gravar(cenario, outra, comZip(livre)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_PECA_COM_FICHA"));
        gravar(cenario, opcional, comZip(livre)).andExpect(status().isOk());
    }

    @Test
    void deveRecusarUmDocumentoQueNaoEPecaGraficaDoProcesso() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        DocumentoConfig outros = new DocumentoConfig(cenario.itens().get("ORD_PUB").getInstrumentoProcedimentoConfig(), "OUTRO_ITEM",
                "Outro", "Outro", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", 40);
        outros = documentoConfigRepository.saveAndFlush(outros);
        UUID documentoOutros = documento(cenario.processo(), outros, cenario.autor(), "outro.zip").getId();
        Cenario alheio = cenario();
        UUID zipAlheio = zipSemFicha(alheio, "ORD_DEP");

        for (UUID invalido : new UUID[] {documentoOutros, zipAlheio, UUID.randomUUID()}) {
            gravar(cenario, opcional, comZip(invalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                    .andExpect(jsonPath("$.campos[*].path").value(contains("processoDocumentoId")));
        }
        assertThat(metadadosMotuRepository.findById(opcional).orElseThrow().getProcessoDocumento()).isNull();
    }

    @Test
    void umaFichaNaoOpcionalNaoMudaDeZip() throws Exception {
        Cenario cenario = cenario();
        UUID livre = zipSemFicha(cenario, "ORD_DEP");

        gravar(cenario, cenario.ordenamentoId(), comZip(livre)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("processoDocumentoId")));
        gravar(cenario, cenario.ordenamentoId(), comZip(zipComFicha(cenario))).andExpect(status().isOk());
    }

    @Test
    void aoRemoverOZipLigadoAOpcionalFicaSemZipENaoSeApaga() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        UUID zip = zipSemFicha(cenario, "ORD_DEP");
        gravar(cenario, opcional, comZip(zip)).andExpect(status().isOk());

        transacao.executeWithoutResult(estado -> {
            metadadosMotuService.removerDoDocumento(zip);
            processoDocumentoRepository.delete(processoDocumentoRepository.findById(zip).orElseThrow());
        });

        Boolean semZip = transacao.execute(estado ->
                metadadosMotuRepository.findById(opcional).orElseThrow().getProcessoDocumento() == null);
        assertThat(semZip).isTrue();
        obter(cenario).andExpect(jsonPath("$.registos[?(@.id == '" + opcional + "')].opcional").value(contains(true)));
    }

    @Test
    void b3_deveRemoverUmaOpcionalEElaSaiDoGet() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);

        remover(cenario, opcional).andExpect(status().isNoContent());

        obter(cenario).andExpect(jsonPath("$.registos.length()").value(3))
                .andExpect(jsonPath("$.registos[?(@.id == '" + opcional + "')]").isEmpty());
        remover(cenario, opcional).andExpect(status().isNotFound());
    }

    @Test
    void b4_deveRecusarRemoverUmaFichaNaoOpcional() throws Exception {
        Cenario cenario = cenario();

        remover(cenario, cenario.ordenamentoId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_NAO_REMOVIVEL"));
        remover(cenario, cenario.serieId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_NAO_REMOVIVEL"));
        obter(cenario).andExpect(jsonPath("$.registos.length()").value(3));
    }

    @Test
    void b5_deveRecusarRemoverForaDeEdicao() throws Exception {
        Cenario cenario = cenario();
        UUID opcional = criarOpcional(cenario);
        colocarEmEstado(cenario, EstadoProcesso.ENVIADO_DGT);

        remover(cenario, opcional).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
        assertThat(metadadosMotuRepository.findById(opcional)).isPresent();
    }

    @Test
    void b6_umaOpcionalIncompletaNaoImpedeASubmissao() {
        Cenario cenario = cenario();
        for (UUID id : new UUID[] {cenario.serieId(), cenario.ordenamentoId(), cenario.condicionantesId()}) {
            transacao.executeWithoutResult(estado -> {
                MetadadosMotu ficha = metadadosMotuRepository.findById(id).orElseThrow();
                ficha.setCompleto(true);
                metadadosMotuRepository.saveAndFlush(ficha);
            });
        }
        transacao.executeWithoutResult(estado -> metadadosMotuRepository.saveAndFlush(MetadadosMotu.conjuntoOpcional(
                processoRepository.findById(cenario.processo().getId()).orElseThrow(), "{}")));

        List<String> pendencias = transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream()
                .map(campo -> campo.path()).filter(caminho -> caminho.startsWith("metadados.")).toList());
        assertThat(pendencias).isEmpty();
    }
}
