package com.conkord.saigt.controller.documento;

import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
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
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.EstruturaDocumental;
import com.conkord.saigt.service.documento.EstruturaDocumentalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstruturaDocumentalControllerIntegrationTest extends StorageTestcontainersBase {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstruturaDocumentalService estruturaDocumentalService;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, InstrumentoProcedimentoConfig procedimento) {
    }

    private Cenario criarCenario(String codigo, String categoriaInstrumento, List<String> elementosAfetados) {
        return criarCenario(codigo, categoriaInstrumento, tipoDinamicaAlteracao(), elementosAfetados);
    }

    /** O código do tipo de Dinâmica é único na base partilhada pelos testes: o tipo partilha-se entre cenários. */
    private TipoDinamica tipoDinamicaAlteracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ALTERACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração")));
    }

    private Cenario criarCenario(String codigo, String categoriaInstrumento, TipoDinamica tipoDinamica,
                                 List<String> elementosAfetados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("ESTD-" + codigo, "Instrumento " + codigo, categoriaInstrumento));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-ESTD-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("509000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-estd.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/ESTD/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setElementosAfetados(elementosAfetados);
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade, procedimento);
    }

    private DocumentoConfig criarItem(Cenario cenario, String codigo, String separador, CategoriaDocumento categoria,
                                      TipoElemento tipoElemento, boolean obrigatorio, int ordem) {
        DocumentoConfig item = new DocumentoConfig(cenario.procedimento(), codigo, "Item " + codigo, separador,
                categoria, tipoElemento, FormatoDocumento.EDITAVEL, "[\"pdf\",\"docx\"]", ordem);
        item.setObrigatorio(obrigatorio);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private DocumentoConfig criarItemMarcado(Cenario cenario, String codigo, String separador, CategoriaDocumento categoria,
                                             TipoElemento tipoElemento, int ordem, ElementoInformacaoBase elemento) {
        DocumentoConfig item = new DocumentoConfig(cenario.procedimento(), codigo, "Item " + codigo, separador,
                categoria, tipoElemento, FormatoDocumento.EDITAVEL, "[\"pdf\",\"docx\"]", ordem);
        item.setElementoInformacaoBase(elemento);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private ProcessoDocumento carregar(Cenario cenario, DocumentoConfig item, String nome) {
        CategoriaDocumento categoria = item == null ? CategoriaDocumento.DEPOSITO : item.getCategoria();
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), item, categoria, "pdf",
                nome, "privado/estd/" + UUID.randomUUID(), HASH, "v1", 10, cenario.autor()));
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor jwtVisitante() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "VISITANTE"));
    }

    private ResultActions pedirEstrutura(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private void definirGeoconform(Cenario cenario, Boolean comGeoconform) {
        Dinamica dinamica = dinamicaRepository.findById(cenario.processo().getDinamica().getId()).orElseThrow();
        dinamica.setComGeoconform(comGeoconform);
        dinamicaRepository.saveAndFlush(dinamica);
    }

    private void criarItensEscritoGraficoEOutros(Cenario cenario) {
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, false, 1);
        criarItem(cenario, "PLANTA", "Planta", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, false, 2);
        criarItem(cenario, "RELATORIO", "Relatório", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, false, 3);
    }

    @Test
    void deveMarcarSoAsPecasGraficasComoObtidasDoGeoconformQuandoADinamicaTemGeoconform() throws Exception {
        Cenario cenario = criarCenario("020", "PROGRAMA", null);
        criarItensEscritoGraficoEOutros(cenario);
        definirGeoconform(cenario, true);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao[0].codigo").value("REGULAMENTO"))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].obtidoDoGeoconform").value(false))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].codigo").value("PLANTA"))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].obtidoDoGeoconform").value(true))
                .andExpect(jsonPath("$.separadores[2].deposito[0].codigo").value("RELATORIO"))
                .andExpect(jsonPath("$.separadores[2].deposito[0].obtidoDoGeoconform").value(false));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void deveDevolverTodosOsItensComObtidoDoGeoconformFalsoQuandoADinamicaNaoTemGeoconform(Boolean comGeoconform)
            throws Exception {
        Cenario cenario = criarCenario(comGeoconform == null ? "021" : "022", "PROGRAMA", null);
        criarItensEscritoGraficoEOutros(cenario);
        definirGeoconform(cenario, comGeoconform);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao[0].obtidoDoGeoconform").value(false))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].obtidoDoGeoconform").value(false))
                .andExpect(jsonPath("$.separadores[2].deposito[0].obtidoDoGeoconform").value(false));
    }

    @Test
    void deveDevolverSoOsItensDosElementosAfetadosQuandoOInstrumentoEPlano() throws Exception {
        Cenario cenario = criarCenario("001", "PLANO", List.of("PECAS_ESCRITAS"));
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, true, 1);
        criarItem(cenario, "PLANTA", "Planta", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, true, 2);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(1))
                .andExpect(jsonPath("$.separadores[0].separador").value("Regulamento"))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].codigo").value("REGULAMENTO"))
                .andExpect(jsonPath("$.pendencias.length()").value(1));
    }

    @Test
    void deveDevolverEstruturaVaziaQuandoPlanoNaoTemElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("002", "PLANO", null);
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, true, 1);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(0))
                .andExpect(jsonPath("$.pendencias.length()").value(0));
    }

    @Test
    void deveDevolverEstruturaVaziaQuandoPlanoTemElementosAfetadosVazios() throws Exception {
        Cenario cenario = criarCenario("003", "PLANO", List.of());
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, true, 1);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(0))
                .andExpect(jsonPath("$.pendencias.length()").value(0));
    }

    @Test
    void naoDeveAplicarOFiltroDosElementosAfetadosNaElaboracaoDeUmPlano() throws Exception {
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream()
                .filter(tipo -> "ELABORACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        Cenario cenario = criarCenario("030", "PLANO", elaboracao, null);
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, true, 1);
        criarItem(cenario, "PLANTA", "Planta", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, true, 2);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(2));
    }

    @Test
    void deveDevolverTodosOsItensAtivosQuandoOInstrumentoNaoEPlano() throws Exception {
        Cenario cenario = criarCenario("004", "PROGRAMA", null);
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, false, 1);
        criarItem(cenario, "PLANTA", "Planta", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, false, 2);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(2));
    }

    @Test
    void naoDeveAplicarOFiltroDosElementosAfetadosQuandoOInstrumentoNaoEPlano() throws Exception {
        Cenario cenario = criarCenario("015", "PROGRAMA", List.of("PECAS_ESCRITAS"));
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, false, 1);
        criarItem(cenario, "PLANTA", "Planta", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, false, 2);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(2));
    }

    @Test
    void deveAgruparOsItensDoSeparadorPorCaixaEOrdenarOsSeparadoresPelaMenorOrdem() throws Exception {
        Cenario cenario = criarCenario("005", "PROGRAMA", null);
        criarItem(cenario, "ATA", "Deliberação", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, false, 2);
        criarItem(cenario, "EDITAL", "Aviso", CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, false, 1);
        criarItem(cenario, "DELIBERACAO", "Deliberação", CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, false, 3);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].separador").value("Aviso"))
                .andExpect(jsonPath("$.separadores[0].deposito.length()").value(0))
                .andExpect(jsonPath("$.separadores[1].separador").value("Deliberação"))
                .andExpect(jsonPath("$.separadores[1].publicacao.length()").value(1))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].codigo").value("DELIBERACAO"))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.separadores[1].deposito.length()").value(1))
                .andExpect(jsonPath("$.separadores[1].deposito[0].codigo").value("ATA"))
                .andExpect(jsonPath("$.separadores[1].deposito[0].extensoesAceites[1]").value("docx"));
    }

    @Test
    void deveDevolverOElementoDaInformacaoBaseDoSeparadorEOmitirNosOutros() throws Exception {
        Cenario cenario = criarCenario("016", "PLANO", List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"));
        criarItemMarcado(cenario, "DELIBERACAO_PUB", "Deliberação", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, 1, ElementoInformacaoBase.DELIBERACAO);
        criarItemMarcado(cenario, "DELIBERACAO_DEP", "Deliberação", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, 2, ElementoInformacaoBase.DELIBERACAO);
        criarItemMarcado(cenario, "TEXTO_ATO_PUB", "Texto do Ato", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, 3, ElementoInformacaoBase.TEXTO_ATO);
        criarItem(cenario, "PLANTA_PUB", "Planta de Localização", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, false, 4);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].separador").value("Deliberação"))
                .andExpect(jsonPath("$.separadores[0].elementoInformacaoBase").value("DELIBERACAO"))
                .andExpect(jsonPath("$.separadores[1].separador").value("Texto do Ato"))
                .andExpect(jsonPath("$.separadores[1].elementoInformacaoBase").value("TEXTO_ATO"))
                .andExpect(jsonPath("$.separadores[2].separador").value("Planta de Localização"))
                .andExpect(jsonPath("$.separadores[2].elementoInformacaoBase").value(nullValue()));
    }

    /** Como no seed da Publicação da Alteração: Texto do Ato e Deliberação são peças escritas com Informação Base. */
    private void criarItensDaPublicacaoDaAlteracao(Cenario cenario) {
        DocumentoConfig textoAto = criarItemMarcado(cenario, "TEXTO_ATO_PUB", "Texto do Ato", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, 1, ElementoInformacaoBase.TEXTO_ATO);
        textoAto.setObrigatorio(true);
        documentoConfigRepository.saveAndFlush(textoAto);
        criarItemMarcado(cenario, "TEXTO_ATO_DEP", "Texto do Ato", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, 2, ElementoInformacaoBase.TEXTO_ATO);
        DocumentoConfig deliberacao = criarItemMarcado(cenario, "DELIBERACAO_PUB", "Deliberação",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, 3, ElementoInformacaoBase.DELIBERACAO);
        deliberacao.setObrigatorio(true);
        documentoConfigRepository.saveAndFlush(deliberacao);
        criarItemMarcado(cenario, "DELIBERACAO_DEP", "Deliberação", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, 4, ElementoInformacaoBase.DELIBERACAO);
        criarItem(cenario, "REGULAMENTO_PUB", "Regulamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, true, 5);
        criarItem(cenario, "ALTERACOES_REGULAMENTO_PUB", "Alterações ao Regulamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, true, 6);
        criarItem(cenario, "PLANTA_ORDENAMENTO_PUB", "Planta de Ordenamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, true, 7);
        criarItem(cenario, "RELATORIO_DEP", "Relatório", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, false, 8);
    }

    @Test
    void deveIncluirOTextoDoAtoEADeliberacaoQuandoSoAsPecasGraficasSaoAfetadas() throws Exception {
        Cenario cenario = criarCenario("040", "PLANO", List.of("PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador")
                        .value(contains("Texto do Ato", "Deliberação", "Planta de Ordenamento")))
                .andExpect(jsonPath("$.outros").isArray())
                .andExpect(jsonPath("$.separadores[0].elementoInformacaoBase").value("TEXTO_ATO"))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].codigo").value("TEXTO_ATO_PUB"))
                .andExpect(jsonPath("$.separadores[0].deposito[0].codigo").value("TEXTO_ATO_DEP"))
                .andExpect(jsonPath("$.separadores[1].elementoInformacaoBase").value("DELIBERACAO"))
                .andExpect(jsonPath("$.separadores[1].publicacao[0].codigo").value("DELIBERACAO_PUB"))
                .andExpect(jsonPath("$.separadores[1].deposito[0].codigo").value("DELIBERACAO_DEP"))
                .andExpect(jsonPath("$.separadores[*].publicacao[*].codigo", not(hasItem("REGULAMENTO_PUB"))))
                .andExpect(jsonPath("$.separadores[*].publicacao[*].codigo", not(hasItem("ALTERACOES_REGULAMENTO_PUB"))))
                .andExpect(jsonPath("$.pendencias[*].codigo")
                        .value(containsInAnyOrder("TEXTO_ATO_PUB", "DELIBERACAO_PUB", "PLANTA_ORDENAMENTO_PUB")));
    }

    @Test
    void deveIncluirOTextoDoAtoEADeliberacaoQuandoSoOsOutrosSaoAfetados() throws Exception {
        Cenario cenario = criarCenario("041", "PLANO", List.of("OUTROS"));
        criarItensDaPublicacaoDaAlteracao(cenario);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Deliberação", "Relatório")))
                .andExpect(jsonPath("$.separadores[2].deposito[0].codigo").value("RELATORIO_DEP"));
    }

    @Test
    void deveManterAEstruturaSemItensRepetidosComPecasEscritasEGraficas() throws Exception {
        Cenario cenario = criarCenario("042", "PLANO", List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Deliberação",
                        "Regulamento", "Alterações ao Regulamento", "Planta de Ordenamento")))
                .andExpect(jsonPath("$.separadores[*].publicacao[*].codigo").value(contains("TEXTO_ATO_PUB",
                        "DELIBERACAO_PUB", "REGULAMENTO_PUB", "ALTERACOES_REGULAMENTO_PUB", "PLANTA_ORDENAMENTO_PUB")))
                .andExpect(jsonPath("$.separadores[*].deposito[*].codigo")
                        .value(contains("TEXTO_ATO_DEP", "DELIBERACAO_DEP")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "vazio")
    void deveManterAEstruturaVaziaSemElementosAfetadosMesmoComItensDaInformacaoBase(String elementos) throws Exception {
        Cenario cenario = criarCenario(elementos == null ? "043" : "044", "PLANO", elementos == null ? null : List.of());
        criarItensDaPublicacaoDaAlteracao(cenario);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(0))
                .andExpect(jsonPath("$.pendencias.length()").value(0));
    }

    @Test
    void naoDeveMudarAEstruturaDaElaboracaoComItensDaInformacaoBase() throws Exception {
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream()
                .filter(tipo -> "ELABORACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        Cenario cenario = criarCenario("045", "PLANO", elaboracao, null);
        criarItensDaPublicacaoDaAlteracao(cenario);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato", "Deliberação",
                        "Regulamento", "Alterações ao Regulamento", "Planta de Ordenamento", "Relatório")));
    }

    @Test
    void deveTomarOElementoDoPrimeiroItemDoSeparadorQueATenha() throws Exception {
        Cenario cenario = criarCenario("017", "PROGRAMA", null);
        criarItem(cenario, "DELIBERACAO_PUB", "Deliberação", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, false, 1);
        criarItemMarcado(cenario, "DELIBERACAO_DEP", "Deliberação", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, 2, ElementoInformacaoBase.DELIBERACAO);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].elementoInformacaoBase").value("DELIBERACAO"));
    }

    @Test
    void deveMarcarPendenteSoOItemObrigatorioSemFicheiros() throws Exception {
        Cenario cenario = criarCenario("006", "PROGRAMA", null);
        DocumentoConfig preenchido = criarItem(cenario, "PREENCHIDO", "Obrigatórios", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, true, 1);
        criarItem(cenario, "EM_FALTA", "Obrigatórios", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, true, 2);
        criarItem(cenario, "OPCIONAL", "Opcionais", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, false, 3);
        carregar(cenario, preenchido, "primeiro.pdf");
        carregar(cenario, preenchido, "segundo.pdf");

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao[0].pendente").value(false))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros.length()").value(2))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros[0].nomeFicheiro").value("primeiro.pdf"))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros[1].nomeFicheiro").value("segundo.pdf"))
                .andExpect(jsonPath("$.separadores[0].deposito[0].pendente").value(true))
                .andExpect(jsonPath("$.separadores[1].deposito[0].pendente").value(false))
                .andExpect(jsonPath("$.pendencias.length()").value(1))
                .andExpect(jsonPath("$.pendencias[0].codigo").value("EM_FALTA"))
                .andExpect(jsonPath("$.pendencias[0].categoria").value("DEPOSITO"))
                .andExpect(jsonPath("$.pendencias[0].motivo").value("Falta carregar um ficheiro ou indicar observações."));
    }

    @Test
    void deveIgnorarOFicheiroSubstituidoNosFicheirosEPendenciasDoItem() throws Exception {
        Cenario cenario = criarCenario("090", "PROGRAMA", null);
        DocumentoConfig item = criarItem(cenario, "OBRIGATORIO", "Obrigatórios", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, true, 1);
        ProcessoDocumento recusado = carregar(cenario, item, "recusado.pdf");
        recusado.setEstadoValidacao(EstadoValidacao.SUBSTITUIDO);
        processoDocumentoRepository.saveAndFlush(recusado);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao[0].pendente").value(true))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros.length()").value(0))
                .andExpect(jsonPath("$.pendencias.length()").value(1))
                .andExpect(jsonPath("$.pendencias[0].codigo").value("OBRIGATORIO"));

        carregar(cenario, item, "novo.pdf");

        pedirEstrutura(cenario)
                .andExpect(jsonPath("$.separadores[0].publicacao[0].pendente").value(false))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros.length()").value(1))
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros[0].nomeFicheiro").value("novo.pdf"))
                .andExpect(jsonPath("$.pendencias.length()").value(0));
    }

    @Test
    void deveColocarOsFicheirosSemItemNoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("007", "PROGRAMA", null);
        DocumentoConfig item = criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, false, 1);
        carregar(cenario, item, "regulamento.pdf");
        carregar(cenario, null, "avulso.pdf");

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outros.length()").value(1))
                .andExpect(jsonPath("$.outros[0].nomeFicheiro").value("avulso.pdf"))
                .andExpect(jsonPath("$.outros[0].documentoConfigId").isEmpty())
                .andExpect(jsonPath("$.separadores[0].publicacao[0].ficheiros.length()").value(1));
    }

    @Test
    void naoDeveDevolverItensInativos() throws Exception {
        Cenario cenario = criarCenario("008", "PROGRAMA", null);
        criarItem(cenario, "ATIVO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, true, 1);
        DocumentoConfig inativo = criarItem(cenario, "INATIVO", "Planta", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, true, 2);
        inativo.setAtivo(false);
        documentoConfigRepository.saveAndFlush(inativo);

        pedirEstrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(1))
                .andExpect(jsonPath("$.separadores[0].separador").value("Regulamento"))
                .andExpect(jsonPath("$.pendencias.length()").value(1));
    }

    @Test
    void devePermitirLeituraAoVisitante() throws Exception {
        Cenario cenario = criarCenario("009", "PROGRAMA", null);
        criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, false, 1);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores.length()").value(1));
    }

    @Test
    void deveDevolver404QuandoProcessoEDeOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("010", "PROGRAMA", null);
        Cenario outraEntidade = criarCenario("011", "PROGRAMA", null);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver404QuandoProcessoNaoExiste() throws Exception {
        Cenario cenario = criarCenario("012", "PROGRAMA", null);

        mockMvc.perform(get("/api/v1/processos/" + UUID.randomUUID() + "/estrutura-documental")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver401SemToken() throws Exception {
        Cenario cenario = criarCenario("013", "PROGRAMA", null);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void montarDeveDevolverOModeloSemContextoDePedidoENemVerificarAutorizacao() {
        Cenario cenario = criarCenario("014", "PROGRAMA", null);
        DocumentoConfig item = criarItem(cenario, "REGULAMENTO", "Regulamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, true, 1);

        EstruturaDocumental estrutura = estruturaDocumentalService.montar(cenario.processo());

        assertThat(estrutura.separadores()).hasSize(1);
        assertThat(estrutura.separadores().get(0).publicacao().get(0).config().getId()).isEqualTo(item.getId());
        assertThat(estrutura.pendencias()).hasSize(1);
        assertThat(estrutura.outros()).isEmpty();
    }
}
