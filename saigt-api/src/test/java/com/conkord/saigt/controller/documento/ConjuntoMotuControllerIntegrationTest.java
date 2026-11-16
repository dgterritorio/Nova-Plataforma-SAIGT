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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A ficha MOTU do Conjunto de Dados Geográficos de cada peça gráfica de publicação: campos, tipos e `completo`. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConjuntoMotuControllerIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9900);
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

    /** Uma folha gravada pela página das Plantas, num ZIP de depósito, que a ligação põe no Conjunto do mesmo nome. */
    private UUID folha(Cenario cenario, String item, String nomeRegulamento, String numeroFolha, int linha) {
        UUID id = transacao.execute(estado -> {
            Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
            ProcessoDocumento zip = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).stream()
                    .filter(documento -> documento.getDocumentoConfig().getId().equals(cenario.itens().get(item).getId()))
                    .findFirst().orElseGet(() -> documento(processo, cenario.itens().get(item), cenario.autor(), item + ".zip"));
            Planta planta = new Planta(processo, zip);
            planta.setNomeRegulamento(nomeRegulamento);
            planta.setNumeroFolha(numeroFolha);
            planta.setNomeTiff("t" + linha + ".tif");
            return plantaRepository.saveAndFlush(planta).getId();
        });
        transacao.executeWithoutResult(estado -> metadadosMotuService.ligarFolhas(cenario.processo().getId()));
        return id;
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

    private ResultActions gravar(Cenario cenario, UUID motuId, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(url(cenario) + "/" + motuId).with(jwtDe(cenario)).contentType("application/json")
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private static Map<String, Object> comuns() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tituloAlternativo", "Planta de Ordenamento do PDM");
        corpo.put("resumo", "Uso do solo");
        corpo.put("palavrasChave", "ordenamento; solo");
        corpo.put("denominadorEscala", "10000");
        corpo.put("sistemaReferencia", "PT-TM06/ETRS89");
        corpo.put("codigoSistema", "3763");
        corpo.put("dataEdicao", "2024-05-01");
        corpo.put("atualizacaoComplemento", "Sem atualizações");
        corpo.put("entidadeProprietaria", "DGT");
        corpo.put("entidadeProdutora", "Município");
        corpo.put("sistemaReferenciaNome", "ETRS89");
        corpo.put("sistemaReferenciaCodigo", "4258");
        corpo.put("exatidaoPosicional", "1 m");
        corpo.put("exatidaoTematica", "95 %");
        return corpo;
    }

    private static Map<String, Object> outro() {
        Map<String, Object> corpo = comuns();
        corpo.put("cartografiaTipo", "OUTRO");
        return corpo;
    }

    private static Map<String, Object> homologada() {
        Map<String, Object> corpo = comuns();
        corpo.put("cartografiaTipo", "CARTOGRAFIA_HOMOLOGADA");
        corpo.put("numeroHomologacao", "H-123");
        corpo.put("dataHomologacao", "2023-01-15");
        corpo.put("entidadeHomologacao", "IGeoE");
        return corpo;
    }

    private static Map<String, Object> comFolhas(Map<String, Object> corpo, Map<String, Object>... folhas) {
        corpo.put("plantas", List.of(folhas));
        return corpo;
    }

    private static Map<String, Object> folhaGravada(UUID id, String nome, String numero) {
        Map<String, Object> folha = new LinkedHashMap<>();
        folha.put("id", id.toString());
        folha.put("nomeRegulamento", nome);
        folha.put("numeroFolha", numero);
        return folha;
    }

    private MetadadosMotu conjunto(UUID id) {
        return metadadosMotuRepository.findById(id).orElseThrow();
    }

    @Test
    void deveDevolverASerieEDoisConjuntosComOTituloDoItemEONomeDoZipPelaOrdemDosItens() throws Exception {
        Cenario cenario = cenario();

        obter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registos[*].tipo").value(contains("SERIE", "CDG", "CDG")))
                .andExpect(jsonPath("$.registos[1].id").value(cenario.ordenamentoId().toString()))
                .andExpect(jsonPath("$.registos[1].titulo").value("Planta de ordenamento"))
                .andExpect(jsonPath("$.registos[1].zip.nome").value("ordenamento-pub.zip"))
                .andExpect(jsonPath("$.registos[1].zip.id").isNotEmpty())
                .andExpect(jsonPath("$.registos[1].completo").value(false))
                .andExpect(jsonPath("$.registos[1].plantas.length()").value(0))
                .andExpect(jsonPath("$.registos[2].id").value(cenario.condicionantesId().toString()))
                .andExpect(jsonPath("$.registos[2].titulo").value("Planta de condicionantes"))
                .andExpect(jsonPath("$.registos[2].zip.nome").value("condicionantes-pub.zip"));
    }

    @Test
    void deveGravarUmConjuntoComOutroEAsFolhasPreenchidasEFicarCompleto() throws Exception {
        Cenario cenario = cenario();
        UUID folha = folha(cenario, "ORD_DEP", "Uso do Solo", "1", 1);

        gravar(cenario, cenario.ordenamentoId(), comFolhas(outro(), folhaGravada(folha, "Uso do Solo", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CDG"))
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.dados.cartografiaTipo").value("OUTRO"))
                .andExpect(jsonPath("$.dados.dataEdicao").value("2024-05-01"))
                .andExpect(jsonPath("$.plantas.length()").value(1));
        obter(cenario).andExpect(jsonPath("$.registos[1].completo").value(true))
                .andExpect(jsonPath("$.registos[2].completo").value(false));
    }

    @Test
    void comCartografiaHomologadaSemADataDaHomologacaoFicaIncompletoEComTodosOsTresFicaCompleto() throws Exception {
        Cenario cenario = cenario();
        UUID folha = folha(cenario, "ORD_DEP", "Uso do Solo", "1", 1);
        Map<String, Object> semData = homologada();
        semData.remove("dataHomologacao");

        gravar(cenario, cenario.ordenamentoId(), comFolhas(semData, folhaGravada(folha, "Uso do Solo", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
        gravar(cenario, cenario.ordenamentoId(), comFolhas(homologada(), folhaGravada(folha, "Uso do Solo", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.dados.numeroHomologacao").value("H-123"))
                .andExpect(jsonPath("$.dados.dataHomologacao").value("2023-01-15"))
                .andExpect(jsonPath("$.dados.entidadeHomologacao").value("IGeoE"));
    }

    @Test
    void aoMudarDeTipoOsCamposDoTipoAnteriorSaemDeDadosEOCompletoPassaADependerDoNovoTipo() throws Exception {
        Cenario cenario = cenario();
        UUID folha = folha(cenario, "ORD_DEP", "Uso do Solo", "1", 1);
        gravar(cenario, cenario.ordenamentoId(), comFolhas(homologada(), folhaGravada(folha, "Uso do Solo", "1")))
                .andExpect(jsonPath("$.completo").value(true));

        Map<String, Object> oficial = comFolhas(comuns(), folhaGravada(folha, "Uso do Solo", "1"));
        oficial.put("cartografiaTipo", "SERIE_CARTOGRAFICA_OFICIAL");
        gravar(cenario, cenario.ordenamentoId(), oficial)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.dados.numeroHomologacao").doesNotExist())
                .andExpect(jsonPath("$.dados.dataHomologacao").doesNotExist())
                .andExpect(jsonPath("$.dados.entidadeHomologacao").doesNotExist());
        assertThat(conjunto(cenario.ordenamentoId()).getDados()).doesNotContain("Homologacao").doesNotContain("H-123");

        oficial.put("serieCartograficaOficial", "Carta Militar 1:25 000");
        gravar(cenario, cenario.ordenamentoId(), oficial)
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.dados.serieCartograficaOficial").value("Carta Militar 1:25 000"));

        gravar(cenario, cenario.ordenamentoId(), comFolhas(outro(), folhaGravada(folha, "Uso do Solo", "1")))
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.dados.serieCartograficaOficial").doesNotExist());
        assertThat(conjunto(cenario.ordenamentoId()).getDados()).doesNotContain("serieCartograficaOficial");
    }

    @Test
    void deveRecusarUmCampoDeOutroTipoOuUmTipoDesconhecidoSemAlterarNada() throws Exception {
        Cenario cenario = cenario();
        folha(cenario, "ORD_DEP", "Uso do Solo", "1", 1);
        gravar(cenario, cenario.ordenamentoId(), outro()).andExpect(status().isOk());

        Map<String, Object> comCampoDeOutroTipo = outro();
        comCampoDeOutroTipo.put("serieCartograficaOficial", "Carta Militar");
        gravar(cenario, cenario.ordenamentoId(), comCampoDeOutroTipo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("serieCartograficaOficial")));

        Map<String, Object> homologacaoComOficial = comuns();
        homologacaoComOficial.put("cartografiaTipo", "SERIE_CARTOGRAFICA_OFICIAL");
        homologacaoComOficial.put("numeroHomologacao", "H-1");
        gravar(cenario, cenario.ordenamentoId(), homologacaoComOficial)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("numeroHomologacao")));

        Map<String, Object> desconhecido = comuns();
        desconhecido.put("cartografiaTipo", "INEXISTENTE");
        gravar(cenario, cenario.ordenamentoId(), desconhecido)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("cartografiaTipo")));

        Map<String, Object> dataInvalida = outro();
        dataInvalida.put("dataEdicao", "31/12/2024");
        gravar(cenario, cenario.ordenamentoId(), dataInvalida)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].path").value(contains("dataEdicao")));

        assertThat(conjunto(cenario.ordenamentoId()).getDados()).contains("OUTRO").doesNotContain("Carta Militar");
    }

    @Test
    void deveRecusarUmCampoAcimaDoLimiteComOCampoNoErroSemAlterarNada() throws Exception {
        Cenario cenario = cenario();
        gravar(cenario, cenario.ordenamentoId(), outro()).andExpect(status().isOk());

        Map<String, Object> acima = outro();
        acima.put("exatidaoPosicional", "x".repeat(101));
        acima.put("entidadeProdutora", "y".repeat(301));
        gravar(cenario, cenario.ordenamentoId(), acima)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("entidadeProdutora", "exatidaoPosicional")));

        assertThat(conjunto(cenario.ordenamentoId()).getDados()).contains("1 m");
    }

    @Test
    void umConjuntoSemFolhasAssociadasGravaMasFicaIncompleto() throws Exception {
        Cenario cenario = cenario();

        gravar(cenario, cenario.condicionantesId(), outro())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.plantas.length()").value(0));
    }

    @Test
    void oCompletoDaSerieNaoDependeDosConjuntos() throws Exception {
        Cenario cenario = cenario();
        Map<String, Object> serie = new LinkedHashMap<>();
        serie.put("tituloAlternativo", "PDM");
        serie.put("resumo", "r");
        serie.put("palavrasChave", "p");
        serie.put("denominadorEscala", "1");
        serie.put("sistemaReferencia", "s");
        serie.put("historico", "h");
        serie.put("relatorio", "r");

        gravar(cenario, cenario.serieId(), serie).andExpect(status().isOk()).andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.zip").doesNotExist());
    }

    private void marcarCompleto(UUID id, boolean completo) {
        transacao.executeWithoutResult(estado -> {
            MetadadosMotu ficha = conjunto(id);
            ficha.setCompleto(completo);
            metadadosMotuRepository.saveAndFlush(ficha);
        });
    }

    private List<String> pendenciasMotu(Cenario cenario) {
        return transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream()
                .map(campo -> campo.path()).filter(caminho -> caminho.startsWith("metadados.")).toList());
    }

    @Test
    void asPendenciasDaSubmissaoPedemASerieEOsConjuntosNaoOpcionaisPorCompletar() {
        Cenario cenario = cenario();

        assertThat(pendenciasMotu(cenario)).containsExactly("metadados.serie",
                "metadados.conjunto." + cenario.ordenamentoId(), "metadados.conjunto." + cenario.condicionantesId());

        marcarCompleto(cenario.serieId(), true);
        marcarCompleto(cenario.ordenamentoId(), true);
        assertThat(pendenciasMotu(cenario)).containsExactly("metadados.conjunto." + cenario.condicionantesId());

        marcarCompleto(cenario.condicionantesId(), true);
        assertThat(pendenciasMotu(cenario)).isEmpty();
    }

    @Test
    void umaFichaOpcionalIncompletaNaoImpedeASubmissao() {
        Cenario cenario = cenario();
        marcarCompleto(cenario.serieId(), true);
        marcarCompleto(cenario.ordenamentoId(), true);
        marcarCompleto(cenario.condicionantesId(), true);
        transacao.executeWithoutResult(estado -> {
            MetadadosMotu opcional = MetadadosMotu.conjunto(processoRepository.findById(cenario.processo().getId()).orElseThrow(), null);
            ReflectionTestUtils.setField(opcional, "opcional", true);
            metadadosMotuRepository.saveAndFlush(opcional);
        });

        assertThat(pendenciasMotu(cenario)).isEmpty();
    }
}
