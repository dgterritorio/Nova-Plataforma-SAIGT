package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
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
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
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
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `PATCH /processos/{id}/submeter` com a ficha MOTU: a Série e cada Conjunto não opcional `completo` são
 * condição da submissão num procedimento final com peça gráfica de publicação aplicável (BL-DOC-003 passo 3,
 * BL-DOC-008 passo 5), pela API HTTP e sem mocks.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubmeterFichaMotuIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(5000);
    private static final String HASH = "b".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;
    @Autowired
    private TransactionTemplate transacao;
    @Autowired
    private MetadadosMotuService metadadosMotuService;
    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;
    @Autowired
    private CompletudeDocumentalService completudeDocumentalService;
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
    private PlantaRepository plantaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig ordenamento,
                           DocumentoConfig condicionantes) {
    }

    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    private TipoDinamica tipoDinamicaAlteracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ALTERACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração")));
    }

    /**
     * A Publicação de uma Alteração de PDM sem GeoConform: único (logo final) procedimento da dinâmica, com um texto de
     * publicação obrigatório e as peças gráficas de publicação de ordenamento (ordem 7) e de condicionantes (ordem 8);
     * com {@code paginaDasPlantas}, também o ZIP de depósito da página das Plantas.
     */
    private Cenario cenario(List<String> elementosAfetados, boolean paginaDasPlantas) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("MOTU-" + n, "Plano " + n, "PLANO"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamicaAlteracao()));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        DocumentoConfig texto = item(procedimento, "TEXTO_PUB", TipoElemento.PECA_ESCRITA, CategoriaDocumento.PUBLICACAO, 1,
                PaginaDocumento.DOCUMENTOS, "pdf", true);
        DocumentoConfig ordenamento = item(procedimento, "PLANTA_ORDENAMENTO_PUB", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.PUBLICACAO, 7, PaginaDocumento.DOCUMENTOS, "zip", false);
        DocumentoConfig condicionantes = item(procedimento, "PLANTA_CONDICIONANTES_PUB", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.PUBLICACAO, 8, PaginaDocumento.DOCUMENTOS, "zip", false);
        if (paginaDasPlantas) {
            item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", TipoElemento.PECA_GRAFICA, CategoriaDocumento.DEPOSITO, 30,
                    PaginaDocumento.PLANTAS, "zip", false);
        }

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5094" + n, "Câmara " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-motu.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/MOTU/" + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        dinamica.setComGeoconform(false);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setElementosAfetados(elementosAfetados);
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        processo = processoRepository.saveAndFlush(processo);
        carregar(processo, texto, autor, "texto.pdf", "pdf");
        return new Cenario(processo, autor, entidade, ordenamento, condicionantes);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, TipoElemento tipo,
                                 CategoriaDocumento categoria, int ordem, PaginaDocumento pagina, String extensao,
                                 boolean obrigatorio) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, "Separador " + codigo, categoria, tipo,
                FormatoDocumento.NAO_EDITAVEL, "[\"" + extensao + "\"]", ordem);
        item.setPagina(pagina);
        item.setObrigatorio(obrigatorio);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private ProcessoDocumento carregar(Processo processo, DocumentoConfig item, Utilizador autor, String nome, String extensao) {
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item, item.getCategoria(), extensao, nome,
                "privado/motu/" + UUID.randomUUID() + "-" + nome, HASH, "v1", 10, autor));
    }

    /** Carrega os ZIP de publicação (o de condicionantes primeiro, para provar a ordem dos itens) e cria a ficha MOTU. */
    private void carregarPecasGraficas(Cenario cenario) {
        ProcessoDocumento condicionantes = carregar(cenario.processo(), cenario.condicionantes(), cenario.autor(),
                "condicionantes.zip", "zip");
        ProcessoDocumento ordenamento = carregar(cenario.processo(), cenario.ordenamento(), cenario.autor(),
                "ordenamento.zip", "zip");
        transacao.executeWithoutResult(estado -> {
            metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(condicionantes.getId()).orElseThrow());
            metadadosMotuService.registarPecaGrafica(processoDocumentoRepository.findById(ordenamento.getId()).orElseThrow());
        });
    }

    private MetadadosMotu serie(Cenario cenario) {
        return metadadosMotuRepository.findByProcesso_IdAndTipo(cenario.processo().getId(), TipoMetadadosMotu.SERIE).orElseThrow();
    }

    private MetadadosMotu conjunto(Cenario cenario, DocumentoConfig item) {
        UUID documento = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()).stream()
                .filter(d -> d.getDocumentoConfig().getId().equals(item.getId())).findFirst().orElseThrow().getId();
        return metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documento, TipoMetadadosMotu.CDG).orElseThrow();
    }

    private void marcarCompleto(MetadadosMotu ficha) {
        transacao.executeWithoutResult(estado -> {
            MetadadosMotu atual = metadadosMotuRepository.findById(ficha.getId()).orElseThrow();
            atual.setCompleto(true);
            metadadosMotuRepository.saveAndFlush(atual);
        });
    }

    private static RequestPostProcessor jwtDoAutor(Utilizador autor, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, autor.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private ResultActions submeter(Processo processo, Utilizador autor, Entidade entidade, String opcaoGeoconform) throws Exception {
        long versao = processoRepository.findById(processo.getId()).orElseThrow().getVersao();
        String corpo = "{\"versao\":" + versao + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"" + opcaoGeoconform + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/submeter")
                .with(jwtDoAutor(autor, entidade)).contentType("application/json").content(corpo));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        return submeter(cenario.processo(), cenario.autor(), cenario.entidade(), "");
    }

    private EstadoProcesso estado(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getEstado();
    }

    /** Série incompleta com os Conjuntos completos. */
    @Test
    void deveRecusarComMetadadosSerieQuandoASerieEstaIncompletaEManterEmSubmissao() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), false);
        carregarPecasGraficas(cenario);
        marcarCompleto(conjunto(cenario, cenario.ordenamento()));
        marcarCompleto(conjunto(cenario, cenario.condicionantes()));
        long versaoAntes = processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLANTAS_METADADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("metadados.serie"))
                .andExpect(jsonPath("$.campos[0].erro").value("Preencha a Série de Dados Geográficos."));

        Processo depois = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
    }

    /** Série completa e dois Conjuntos não opcionais incompletos, pela ordem do item e sem `metadados.serie`. */
    @Test
    void deveRecusarComUmaPendenciaPorConjuntoIncompletoPelaOrdemDoItemESemASerie() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), false);
        carregarPecasGraficas(cenario);
        marcarCompleto(serie(cenario));
        MetadadosMotu ordenamento = conjunto(cenario, cenario.ordenamento());
        MetadadosMotu condicionantes = conjunto(cenario, cenario.condicionantes());

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLANTAS_METADADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("metadados.conjunto." + ordenamento.getId(),
                        "metadados.conjunto." + condicionantes.getId())))
                .andExpect(jsonPath("$.campos[0].erro").value("Item PLANTA_ORDENAMENTO_PUB: faltam «Título Alternativo», «Resumo», «Palavras-chave» e mais 12 campos."))
                .andExpect(jsonPath("$.campos[*].path").value(not(hasItem("metadados.serie"))));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    /** a Série e todos os Conjuntos não opcionais `completo` e o resto satisfeito. */
    @Test
    void deveSubmeterQuandoASerieETodosOsConjuntosEstaoCompletos() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), false);
        carregarPecasGraficas(cenario);
        marcarCompleto(serie(cenario));
        marcarCompleto(conjunto(cenario, cenario.ordenamento()));
        marcarCompleto(conjunto(cenario, cenario.condicionantes()));

        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    /** uma ficha de Conjunto opcional incompleta, e sem ZIP, não é exigida. */
    @Test
    void naoDeveExigirUmaFichaDeConjuntoOpcionalIncompletaESemZip() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), false);
        carregarPecasGraficas(cenario);
        marcarCompleto(serie(cenario));
        marcarCompleto(conjunto(cenario, cenario.ordenamento()));
        marcarCompleto(conjunto(cenario, cenario.condicionantes()));
        transacao.executeWithoutResult(estado -> {
            MetadadosMotu opcional = MetadadosMotu.conjunto(processoRepository.findById(cenario.processo().getId()).orElseThrow(), null);
            ReflectionTestUtils.setField(opcional, "opcional", true);
            metadadosMotuRepository.saveAndFlush(opcional);
        });

        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    /** sem registo de Série, `metadados.serie` aparece nas pendências. */
    @Test
    void deveExigirASerieQuandoOProcessoNaoTemRegistoDeSerie() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), false);
        assertThat(metadadosMotuRepository.findByProcesso_IdAndTipo(cenario.processo().getId(), TipoMetadadosMotu.SERIE)).isEmpty();

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLANTAS_METADADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem("metadados.serie")));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    /** Sem mocks: uma Alteração só com «Peças Escritas» não exige a ficha MOTU nem a ficha única. */
    @Test
    void umaAlteracaoSoComPecasEscritasSubmeteSemAFichaMotu() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS"), true);
        assertThat(metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
        List<?> pendencias = transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()));
        assertThat(pendencias).isEmpty();

        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
    }

    /** as pendências das Plantas e as da ficha MOTU vêm juntas, num único 400. */
    @Test
    void devePedirNumSoBadRequestAsPendenciasDasPlantasEDaFichaMotu() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), true);
        carregarPecasGraficas(cenario);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLANTAS_METADADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItems("plantas.zips", "metadados.serie")))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem(startsWith("metadados.conjunto."))));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    /**
     * Decisão de elaborar (não é o procedimento final): sem GeoConform continua a pedir a ficha única (`metadados.*`),
     * nunca a Série nem os Conjuntos.
     */
    @Test
    void aDecisaoDeElaborarSemGeoconformContinuaAPedirAFichaUnica() throws Exception {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("MOTU-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        InstrumentoProcedimentoConfig decisao = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig,
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("MOTU-FIM-" + n, "Procedimento final")), 2, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, decisao));
        item(decisao, "PLANTAS_ZIP_ORDENAMENTO_DEP", TipoElemento.PECA_GRAFICA, CategoriaDocumento.DEPOSITO, 30,
                PaginaDocumento.PLANTAS, "zip", false);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5095" + n, "Câmara Elaborar " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-elab.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/ELAB/" + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Elaboração do PDM", autor);
        dinamica.setComGeoconform(false);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = new Processo(dinamica, decisao, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        Processo criado = processoRepository.saveAndFlush(processo);

        submeter(criado, autor, entidade, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLANTAS_METADADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[*].path").value(hasItem(startsWith("metadados."))))
                .andExpect(jsonPath("$.campos[*].path").value(not(hasItem("metadados.serie"))))
                .andExpect(jsonPath("$.campos[*].path").value(not(hasItem(startsWith("metadados.conjunto.")))));

        assertThat(estado(criado)).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(metadadosMotuRepository.findByProcesso_Id(criado.getId())).isEmpty();
    }

    /**
     * O caso do ensaio: o Conjunto da Planta de Ordenamento preenchido, com uma folha da página das Plantas sem a
     * «Folha N». O detalhe diz a página dos Metadados, a ficha e o campo.
     */
    @Test
    void oDetalheDizQualEAFichaEOCampoEmFaltaNoConjuntoMotu() throws Exception {
        Cenario cenario = cenario(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"), true);
        carregarPecasGraficas(cenario);
        marcarCompleto(serie(cenario));
        marcarCompleto(conjunto(cenario, cenario.condicionantes()));
        MetadadosMotu ordenamento = conjunto(cenario, cenario.ordenamento());
        DocumentoConfig zipDasPlantas = documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(cenario.processo().getInstrumentoProcedimentoConfig())
                .stream().filter(item -> "PLANTAS_ZIP_ORDENAMENTO_DEP".equals(item.getCodigo())).findFirst().orElseThrow();
        ProcessoDocumento zip = carregar(cenario.processo(), zipDasPlantas, cenario.autor(), "ordenamento-dep.zip", "zip");
        transacao.executeWithoutResult(estado -> {
            MetadadosMotu ficha = metadadosMotuRepository.findById(ordenamento.getId()).orElseThrow();
            ficha.setDados("{\"tituloAlternativo\":\"Ordenamento\",\"resumo\":\"R\",\"palavrasChave\":\"P\","
                    + "\"denominadorEscala\":\"10000\",\"sistemaReferencia\":\"PT-TM06\",\"cartografiaTipo\":\"OUTRO\","
                    + "\"dataEdicao\":\"2026-01-01\",\"atualizacaoComplemento\":\"C\",\"entidadeProprietaria\":\"E\","
                    + "\"entidadeProdutora\":\"E\",\"sistemaReferenciaNome\":\"N\",\"sistemaReferenciaCodigo\":\"3763\","
                    + "\"exatidaoPosicional\":\"1 m\",\"exatidaoTematica\":\"Alta\"}");
            ficha.setCompleto(false);
            metadadosMotuRepository.saveAndFlush(ficha);
            Planta folha = new Planta(processoRepository.findById(cenario.processo().getId()).orElseThrow(),
                    processoDocumentoRepository.findById(zip.getId()).orElseThrow());
            folha.setMetadadosMotu(ficha);
            folha.setNomeRegulamento("Planta de Ordenamento");
            plantaRepository.saveAndFlush(folha);
        });

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario.autor(), cenario.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.pagina == 'METADADOS')].campo")
                        .value(contains("metadados.conjunto." + ordenamento.getId())))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.pagina == 'METADADOS')].erro")
                        .value(contains("Item PLANTA_ORDENAMENTO_PUB: falta a Folha N na tabela Plantas SAIGT SNIT.")));
    }
}
