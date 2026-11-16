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
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
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
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubmeterCompletudeDocumentalIntegrationTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

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
    private MetadadosMotuRepository metadadosMotuRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, InstrumentoProcedimentoConfig procedimento) {
    }
    
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Os itens dependem dos elementos afetados só na Alteração, pelo código: o tipo partilha-se entre cenários. */
    private TipoDinamica tipoDinamicaAlteracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ALTERACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração")));
    }

    private Cenario criarCenario(String codigo, List<String> elementosAfetados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("CMPL-" + codigo, "Instrumento " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaAlteracao();
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoComFormulario();
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5091" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-cmpl.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/CMPL/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setElementosAfetados(elementosAfetados);
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade, procedimento);
    }

    /** Os três itens de publicação do procedimento e os três de depósito, como no seed da Decisão de início. */
    private void criarItensDaDecisaoDeInicio(Cenario cenario) {
        criarItem(cenario, "DELIBERACAO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, 1);
        criarItem(cenario, "DELIBERACAO_DEP", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, 2);
        criarItem(cenario, "TEXTO_ATO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, 3);
        criarItem(cenario, "TEXTO_ATO_DEP", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, 4);
        criarItem(cenario, "PLANTA_LOCALIZACAO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, 5);
        criarItem(cenario, "PLANTA_LOCALIZACAO_DEP", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, 6);
    }

    private DocumentoConfig criarItem(Cenario cenario, String codigo, CategoriaDocumento categoria,
                                      TipoElemento tipoElemento, int ordem) {
        DocumentoConfig item = new DocumentoConfig(cenario.procedimento(), codigo, "Item " + codigo, "Separador " + codigo,
                categoria, tipoElemento, FormatoDocumento.EDITAVEL, "[\"pdf\"]", ordem);
        item.setObrigatorio(categoria == CategoriaDocumento.PUBLICACAO);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private void carregar(Cenario cenario, String codigoItem) {
        DocumentoConfig item = documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(cenario.procedimento()).stream()
                .filter(candidato -> candidato.getCodigo().equals(codigoItem)).findFirst().orElseThrow();
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), item, item.getCategoria(), "pdf",
                codigoItem + ".pdf", "privado/cmpl/" + UUID.randomUUID(), HASH, "v1", 10, cenario.autor()));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private ResultActions submeter(Cenario cenario, String sumario) throws Exception {
        long versao = processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
        String corpo = "{\"versao\":" + versao + ",\"sumario\":" + (sumario == null ? "null" : "\"" + sumario + "\"")
                + ",\"dataAssinatura\":\"" + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\","
                + "\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private Processo recarregar(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    @Test
    void deveRecusarComAsPendenciasDePublicacaoENaoAlterarOProcessoQuandoNaoHaFicheiros() throws Exception {
        Cenario cenario = criarCenario("0001", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        long versaoAntes = recarregar(cenario).getVersao();

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0].path").value("documentos.DELIBERACAO_PUB"))
                .andExpect(jsonPath("$.campos[0].erro").value("Falta carregar: Item DELIBERACAO_PUB (PUBLICACAO)."))
                .andExpect(jsonPath("$.campos[1].path").value("documentos.TEXTO_ATO_PUB"));

        Processo depois = recarregar(cenario);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(depois.getIncmEnrollmentId()).isNull();
    }

    @Test
    void deveSubmeterQuandoOsFicheirosDePublicacaoEstaoCarregados() throws Exception {
        Cenario cenario = criarCenario("0002", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "TEXTO_ATO_PUB");

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveExigirAPlantaSoQuandoOsElementosAfetadosIncluemPecasGraficas() throws Exception {
        Cenario comGraficas = criarCenario("0003", List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS"));
        criarItensDaDecisaoDeInicio(comGraficas);
        carregar(comGraficas, "DELIBERACAO_PUB");
        carregar(comGraficas, "TEXTO_ATO_PUB");
        Cenario semGraficas = criarCenario("0004", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(semGraficas);

        submeter(comGraficas, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("documentos.PLANTA_LOCALIZACAO_PUB"));

        assertThat(completudeDocumentalService.pendencias(semGraficas.processo()))
                .extracting(item -> item.config().getCodigo())
                .doesNotContain("PLANTA_LOCALIZACAO_PUB");
    }

    @Test
    void deveAceitarUmItemDeDepositoObrigatorioSatisfeitoSoPorTag() throws Exception {
        Cenario cenario = criarCenario("0010", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        DocumentoConfig textoAtoDep = itemPorCodigo(cenario, "TEXTO_ATO_DEP");
        textoAtoDep.setObrigatorio(true);
        documentoConfigRepository.saveAndFlush(textoAtoDep);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "TEXTO_ATO_PUB");

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("documentos.TEXTO_ATO_DEP"));

        DocumentoConfig deliberacaoDep = itemPorCodigo(cenario, "DELIBERACAO_DEP");
        ProcessoDocumento comTag = new ProcessoDocumento(cenario.processo(), deliberacaoDep, deliberacaoDep.getCategoria(),
                "pdf", "deliberacao.pdf", "privado/cmpl/" + UUID.randomUUID(), HASH, "v1", 10, cenario.autor());
        comTag.associarTags(List.of(textoAtoDep));
        processoDocumentoRepository.saveAndFlush(comTag);

        assertThat(completudeDocumentalService.pendencias(cenario.processo())).isEmpty();
        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    /** Como no seed da Publicação da Alteração: Texto do Ato e Deliberação com Informação Base, sem condição dos elementos. */
    private void criarItensDaPublicacaoDaAlteracao(Cenario cenario) {
        criarItemDaInformacaoBase(cenario, "TEXTO_ATO_PUB", CategoriaDocumento.PUBLICACAO, 1, ElementoInformacaoBase.TEXTO_ATO);
        criarItemDaInformacaoBase(cenario, "TEXTO_ATO_DEP", CategoriaDocumento.DEPOSITO, 2, ElementoInformacaoBase.TEXTO_ATO);
        criarItemDaInformacaoBase(cenario, "DELIBERACAO_PUB", CategoriaDocumento.PUBLICACAO, 3, ElementoInformacaoBase.DELIBERACAO);
        criarItemDaInformacaoBase(cenario, "DELIBERACAO_DEP", CategoriaDocumento.DEPOSITO, 4, ElementoInformacaoBase.DELIBERACAO);
        criarItem(cenario, "REGULAMENTO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, 5);
        criarItem(cenario, "ALTERACOES_REGULAMENTO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, 6);
        criarItem(cenario, "PLANTA_ORDENAMENTO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, 7);
    }

    private void criarItemDaInformacaoBase(Cenario cenario, String codigo, CategoriaDocumento categoria, int ordem,
                                           ElementoInformacaoBase elemento) {
        DocumentoConfig item = criarItem(cenario, codigo, categoria, TipoElemento.PECA_ESCRITA, ordem);
        item.setElementoInformacaoBase(elemento);
        documentoConfigRepository.saveAndFlush(item);
    }

    private void completarDadosDaSubmissao(Cenario cenario) {
        Processo processo = recarregar(cenario);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processoRepository.saveAndFlush(processo);
        // Procedimento final com peça gráfica de publicação: aplica-se a ficha MOTU, e a Série tem de estar completa.
        MetadadosMotu serie = MetadadosMotu.serie(processo);
        serie.setCompleto(true);
        metadadosMotuRepository.saveAndFlush(serie);
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    @Test
    void deveSubmeterComSoPecasGraficasQuandoOTextoDoAtoEADeliberacaoEstaoCarregados() throws Exception {
        Cenario cenario = criarCenario("0011", List.of("PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);
        completarDadosDaSubmissao(cenario);
        assertThat(completudeDocumentalService.pendencias(cenario.processo()))
                .extracting(item -> item.config().getCodigo())
                .containsExactly("TEXTO_ATO_PUB", "DELIBERACAO_PUB", "PLANTA_ORDENAMENTO_PUB");
        carregar(cenario, "TEXTO_ATO_PUB");
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "PLANTA_ORDENAMENTO_PUB");

        assertThat(completudeDocumentalService.pendencias(cenario.processo())).isEmpty();
        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void naoDevePoderSubmeterComSoPecasGraficasSemOTextoDoAtoCarregado() throws Exception {
        Cenario cenario = criarCenario("0012", List.of("PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);
        completarDadosDaSubmissao(cenario);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "PLANTA_ORDENAMENTO_PUB");

        assertThat(completudeDocumentalService.pendencias(cenario.processo()))
                .extracting(item -> item.config().getCodigo())
                .containsExactly("TEXTO_ATO_PUB");
        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("documentos.TEXTO_ATO_PUB"));

        carregar(cenario, "TEXTO_ATO_PUB");
        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
    }

    @Test
    void deveExigirOTextoDoAtoEADeliberacaoQuandoSoOsOutrosSaoAfetados() {
        Cenario cenario = criarCenario("0013", List.of("OUTROS"));
        criarItensDaPublicacaoDaAlteracao(cenario);

        assertThat(completudeDocumentalService.pendencias(cenario.processo()))
                .extracting(item -> item.config().getCodigo())
                .containsExactly("TEXTO_ATO_PUB", "DELIBERACAO_PUB");
    }

    @Test
    void naoDeveExigirNenhumItemSemElementosAfetadosMesmoComItensDaInformacaoBase() {
        Cenario cenario = criarCenario("0014", List.of());
        criarItensDaPublicacaoDaAlteracao(cenario);

        assertThat(completudeDocumentalService.pendencias(cenario.processo())).isEmpty();
    }

    private DocumentoConfig itemPorCodigo(Cenario cenario, String codigo) {
        return documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(cenario.procedimento())
                .stream().filter(item -> item.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    void deveRecusarPrimeiroOsDadosIncmEmFaltaQuandoFaltamTambemDocumentos() throws Exception {
        Cenario cenario = criarCenario("0005", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);

        submeter(cenario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"));
    }

    @Test
    void deveContinuarARecusarQuandoSoHaFicheirosDeDeposito() throws Exception {
        Cenario cenario = criarCenario("0006", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        carregar(cenario, "DELIBERACAO_DEP");
        carregar(cenario, "TEXTO_ATO_DEP");

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(2));
    }

    @Test
    void deveDevolverListaVaziaDePendenciasQuandoNaoFaltaNenhumFicheiro() {
        Cenario cenario = criarCenario("0007", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "TEXTO_ATO_PUB");

        assertThat(completudeDocumentalService.pendencias(cenario.processo())).isEmpty();
    }

    @Test
    void deveRecusarAResubmissaoDepoisDeCorrigirQuandoFaltamFicheiros() throws Exception {
        Cenario cenario = criarCenario("0008", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        Processo processo = recarregar(cenario);
        processo.setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(processo);

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        long versao = recarregar(cenario).getVersao();
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/corrigir")
                        .with(jwtDoAutor(cenario))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao + "}"))
                .andExpect(status().isOk());

        submeter(cenario, "Resumo do ato.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"));

        assertThat(recarregar(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void naoDeveAlterarOsFicheirosDoProcessoQuandoARecusaOcorre() throws Exception {
        Cenario cenario = criarCenario("0009", List.of("PECAS_ESCRITAS"));
        criarItensDaDecisaoDeInicio(cenario);
        carregar(cenario, "DELIBERACAO_PUB");
        List<UUID> antes = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())
                .stream().map(ProcessoDocumento::getId).toList();

        submeter(cenario, "Resumo do ato.").andExpect(status().isBadRequest());

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())
                .stream().map(ProcessoDocumento::getId).toList()).isEqualTo(antes);
    }
    /** Sem data de assinatura e sem o Texto do Ato: as duas pendências são dos Documentos, com as mensagens da submissão. */
    @Test
    void oDetalheDizQueFaltamADataDeAssinaturaEOTextoDoAtoNaPaginaDosDocumentos() throws Exception {
        Cenario cenario = criarCenario("0020", List.of("PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);
        completarDadosDaSubmissao(cenario);
        Processo processo = recarregar(cenario);
        processo.setDataAssinatura(null);
        processoRepository.saveAndFlush(processo);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "PLANTA_ORDENAMENTO_PUB");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao.length()").value(2))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].pagina").value("DOCUMENTOS"))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].campo").value("dataAssinatura"))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].erro")
                        .value("Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."))
                .andExpect(jsonPath("$.pendenciasSubmissao[1].pagina").value("DOCUMENTOS"))
                .andExpect(jsonPath("$.pendenciasSubmissao[1].campo").value("TEXTO_ATO_PUB"))
                .andExpect(jsonPath("$.pendenciasSubmissao[1].erro").value("Falta carregar: Item TEXTO_ATO_PUB."));
    }

    /** `podeSubmeter` é verdadeiro exatamente quando o processo é editável e a lista está vazia. */
    @Test
    void podeSubmeterSoQuandoNaoHaPendenciasDeSubmissao() throws Exception {
        Cenario cenario = criarCenario("0021", List.of("PECAS_GRAFICAS"));
        criarItensDaPublicacaoDaAlteracao(cenario);
        completarDadosDaSubmissao(cenario);
        carregar(cenario, "DELIBERACAO_PUB");
        carregar(cenario, "PLANTA_ORDENAMENTO_PUB");
        detalhe(cenario)
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao[*].campo").value(contains("TEXTO_ATO_PUB")));

        carregar(cenario, "TEXTO_ATO_PUB");

        detalhe(cenario)
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.podeSubmeter").value(true))
                .andExpect(jsonPath("$.pendenciasSubmissao").isEmpty());
    }

    @Test
    void oDetalheDizQueFaltamOsElementosAfetadosNoFormulario() throws Exception {
        Cenario cenario = criarCenario("0022", null);
        criarItensDaPublicacaoDaAlteracao(cenario);
        completarDadosDaSubmissao(cenario);

        detalhe(cenario)
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao.length()").value(1))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].pagina").value("FORMULARIO"))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].campo").value("elementosAfetados"))
                .andExpect(jsonPath("$.pendenciasSubmissao[0].erro").value("Indique os elementos fundamentais afetados."));
    }

    /** Fora de edição (já submetido) não há nada a corrigir no assistente, mesmo com dados em falta. */
    @Test
    void oDetalheDeUmProcessoNaoEditavelNaoTemPendencias() throws Exception {
        Cenario cenario = criarCenario("0023", null);
        criarItensDaPublicacaoDaAlteracao(cenario);
        Processo processo = recarregar(cenario);
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao").isEmpty());
    }
}
