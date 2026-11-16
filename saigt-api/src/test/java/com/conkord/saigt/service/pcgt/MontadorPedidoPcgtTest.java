package com.conkord.saigt.service.pcgt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.pcgt.PedidoAtualizacaoProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.PedidoCriacaoProcessoPcgt;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MontadorPedidoPcgtTest {

    private static final UUID INSTRUMENTO_ID = UUID.randomUUID();
    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final String IDUS = "01.1111.01.000123.02.00042.01.031.01.20261024";
    private static final String URL = "https://saigt.exemplo.pt/api/v1/publico/";

    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final InstrumentoConcelhoRepository concelhoRepository = mock(InstrumentoConcelhoRepository.class);
    private final ProcessoDocumentoRepository documentoRepository = mock(ProcessoDocumentoRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final NumeroDepositoService numeroDepositoService = mock(NumeroDepositoService.class);
    private final MontadorPedidoPcgt montador = new MontadorPedidoPcgt(idusRepository, concelhoRepository,
            documentoRepository, processoRepository, numeroDepositoService, new ObjectMapper());

    private final Processo processo = mock(Processo.class);
    private final Dinamica dinamica = mock(Dinamica.class);
    private final Instrumento instrumento = mock(Instrumento.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final TipoDinamica tipoDinamica = mock(TipoDinamica.class);
    private final TipoProcedimento tipoProcedimento = mock(TipoProcedimento.class);
    private final Entidade entidade = mock(Entidade.class);
    private final List<ProcessoDocumento> documentos = new ArrayList<>();
    private final Map<UUID, String> urls = new HashMap<>();

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MontadorPedidoPcgt.class);

    @BeforeEach
    void processoPublicadoDaDecisaoDeInicioDeUmaAlteracaoDoPdmDeSintra() {
        logs.start();
        logger.addAppender(logs);
        InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        TipoAto tipoAto = mock(TipoAto.class);
        when(instrumento.getId()).thenReturn(INSTRUMENTO_ID);
        when(instrumento.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(instrumento.getDesignacao()).thenReturn("Plano Diretor Municipal de Sintra");
        when(processo.getEntidade()).thenReturn(entidade);
        when(entidade.getCodigo()).thenReturn("CM-SINTRA");
        when(tipoInstrumento.getCodigo()).thenReturn("PDM");
        when(tipoInstrumento.getPcgtCodigo()).thenReturn("01");
        when(tipoDinamica.getNome()).thenReturn("Alteração");
        when(tipoDinamica.getPcgtCodigo()).thenReturn("02");
        when(dinamicaConfig.getTipoDinamica()).thenReturn(tipoDinamica);
        when(dinamica.getInstrumento()).thenReturn(instrumento);
        when(dinamica.instrumentoDeInicio()).thenReturn(Optional.of(instrumento));
        when(dinamica.designacao()).thenReturn("Plano Diretor Municipal de Sintra");
        when(dinamicaConfig.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        when(dinamica.getNumeroDinamica()).thenReturn("00042");
        when(procedimento.getTipoProcedimento()).thenReturn(tipoProcedimento);
        when(tipoProcedimento.getCodigo()).thenReturn("DECISAO_INICIO");
        when(tipoAto.getNome()).thenReturn("Aviso");
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getNumeroProcesso()).thenReturn("00107");
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(processo.getTipoAto()).thenReturn(tipoAto);
        when(processo.getNumeroDr()).thenReturn("DR-2026-000001");
        when(processo.getDataPublicacaoDr()).thenReturn(LocalDate.of(2026, 10, 24));
        when(processo.getLinkDr()).thenReturn("https://dr.exemplo.pt/aviso/1-2026");
        idus(IDUS);
        concelhos("1111");
        when(documentoRepository.findAtuaisDoProcessoPorCodigos(eq(PROCESSO_ID), anyCollection())).thenReturn(documentos);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private void idus(String valor) {
        IdusProcesso idus = mock(IdusProcesso.class);
        when(idus.getIdus()).thenReturn(valor);
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.ofNullable(valor == null ? null : idus));
    }

    /** O repositório devolve-os por DTCC, como a consulta. */
    private void concelhos(String... dtccs) {
        List<InstrumentoConcelho> lista = Arrays.stream(dtccs).map(dtcc -> new InstrumentoConcelho(instrumento, dtcc))
                .toList();
        when(concelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(INSTRUMENTO_ID)).thenReturn(lista);
    }

    private UUID documento(String codigo, String nome) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getCodigo()).thenReturn(codigo);
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        UUID id = UUID.randomUUID();
        when(documento.getId()).thenReturn(id);
        when(documento.getNomeFicheiro()).thenReturn(nome);
        when(documento.getDocumentoConfig()).thenReturn(item);
        documentos.add(documento);
        urls.put(id, URL + "token-" + nome + "/" + nome);
        return id;
    }

    private void decisaoDeInicioPublicada(String dadosFormulario) {
        Processo decisao = mock(Processo.class);
        when(decisao.getDadosFormulario()).thenReturn(dadosFormulario);
        when(processoRepository.findFirstByDinamicaAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByCriadoEmDesc(
                dinamica, "DECISAO_INICIO", EstadoProcesso.PUBLICADO)).thenReturn(Optional.of(decisao));
    }

    private static String comDataDaDeliberacao(String data) {
        return "{\"informacaoBase\": {\"deliberacao\": {\"dataDeliberacao\": \"" + data + "\"}}}";
    }

    private List<String> avisos() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void deveMontarOPutComOIdusOConcelhoEOAtoSemOsCamposDaCriacao() {
        PedidoAtualizacaoProcessoPcgt pedido = montador.montarAtualizacao(montador.preparar(processo), urls);

        assertThat(pedido.codigoProcesso()).isEqualTo("00042");
        assertThat(pedido.codigoIdus()).isEqualTo(IDUS).matches("(\\d+\\.){9}\\d+");
        assertThat(pedido.municipioToken()).isEqualTo("1111");
        assertThat(pedido.tipoAto()).isEqualTo("Aviso");
        assertThat(pedido.numeroAto()).isEqualTo("DR-2026-000001");
        assertThat(pedido.dataAto()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(pedido.linkAto()).isEqualTo("https://dr.exemplo.pt/aviso/1-2026");
        assertThat(pedido.constituicaoComissaoConsultiva()).isNull();
        assertThat(pedido.ratificacao()).isNull();
        assertThat(pedido.aprovacao()).isNull();
        assertThat(pedido.deposito()).isNull();
        assertThat(avisos()).isEmpty();
    }

    @Test
    void deveMontarOPostComADesignacaoOsTiposEOCodigoDaEntidade() {
        PedidoCriacaoProcessoPcgt pedido = montador.montarCriacao(montador.preparar(processo), urls).orElseThrow();

        assertThat(pedido.codigoProcesso()).isEqualTo("00042");
        assertThat(pedido.idSSAIGT()).isNull();
        assertThat(pedido.numeroProcessoAntigo()).isNull();
        assertThat(pedido.designacao()).isEqualTo("Alteração — Plano Diretor Municipal de Sintra");
        assertThat(pedido.tipoInstrumentoCodigo()).isEqualTo("01");
        assertThat(pedido.tipoProcedimentoCodigo()).isEqualTo("02");
        assertThat(pedido.entidade().codigoEntidade()).isEqualTo("CM-SINTRA");
        assertThat(pedido.municipioToken()).isEqualTo("1111");
        assertThat(pedido.codigoIdus()).isEqualTo(IDUS);
        assertThat(pedido.tipoAto()).isEqualTo("Aviso");
        assertThat(pedido.dataAto()).isEqualTo(LocalDate.of(2026, 10, 24));
    }

    @Test
    void naoDeveLevarAPcgtNenhumaInformacaoDoAproveitamentoDosAtosAnteriores() throws Exception {
        when(processo.getTentativa()).thenReturn(2);
        when(processo.getAproveitaAtosAnteriores()).thenReturn(true);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();

        DadosPedidoPcgt dados = montador.preparar(processo);
        String criacao = json.writeValueAsString(montador.montarCriacao(dados, urls).orElseThrow());
        String atualizacao = json.writeValueAsString(montador.montarAtualizacao(dados, urls));

        assertThat(criacao).doesNotContainIgnoringCase("aproveit");
        assertThat(atualizacao).doesNotContainIgnoringCase("aproveit");
        verify(processo, never()).getAproveitaAtosAnteriores();
        assertThat(Stream.of(DadosPedidoPcgt.class, PedidoCriacaoProcessoPcgt.class, PedidoAtualizacaoProcessoPcgt.class)
                .flatMap(tipo -> Arrays.stream(tipo.getRecordComponents())).map(RecordComponent::getName))
                .noneMatch(nome -> nome.toLowerCase().contains("aproveit"));
    }

    @Test
    void deveUsarOConcelhoDeDtccMaisBaixo() {
        concelhos("1106", "1312");

        assertThat(montador.montarAtualizacao(montador.preparar(processo), urls).municipioToken()).isEqualTo("1106");
    }

    @Test
    void deveOmitirOConcelhoComUmWarnQuandoOInstrumentoNaoTemConcelhos() {
        concelhos();

        PedidoAtualizacaoProcessoPcgt pedido = montador.montarAtualizacao(montador.preparar(processo), urls);

        assertThat(pedido.municipioToken()).isNull();
        assertThat(pedido.codigoProcesso()).isEqualTo("00042");
        assertThat(avisos()).singleElement().asString().contains("municipioToken").contains(PROCESSO_ID.toString());
    }

    @Test
    void deveOmitirOIdusComUmWarnQuandoNaoTemOs10GruposOuNaoExiste() {
        idus("01.1111.01.000123.02.00042.01.031.01");
        assertThat(montador.montarAtualizacao(montador.preparar(processo), urls).codigoIdus()).isNull();

        idus(null);
        assertThat(montador.montarAtualizacao(montador.preparar(processo), urls).codigoIdus()).isNull();

        assertThat(avisos()).hasSize(2).allSatisfy(aviso -> assertThat(aviso).contains("codigoIdus"));
    }

    @Test
    void naoDeveMontarOPostSemOCodigoPcgtDoTipoDeDinamicaENomeiaOCampo() {
        when(tipoDinamica.getPcgtCodigo()).thenReturn(null);

        assertThat(montador.montarCriacao(montador.preparar(processo), urls)).isEmpty();
        assertThat(avisos()).singleElement().asString().contains("tipo_dinamica.pcgt_codigo");
    }

    @Test
    void naoDeveMontarOPostSemOCodigoPcgtDoTipoDeInstrumentoNemSemOCodigoDaEntidade() {
        when(tipoInstrumento.getPcgtCodigo()).thenReturn(null);
        assertThat(montador.montarCriacao(montador.preparar(processo), urls)).isEmpty();

        when(tipoInstrumento.getPcgtCodigo()).thenReturn("01");
        when(entidade.getCodigo()).thenReturn(null);
        assertThat(montador.montarCriacao(montador.preparar(processo), urls)).isEmpty();

        assertThat(avisos()).hasSize(2);
        assertThat(avisos().get(0)).contains("tipo_instrumento.pcgt_codigo");
        assertThat(avisos().get(1)).contains("entidade.codigo");
    }

    @Test
    void deveMontarOBlocoDaComissaoConsultivaComOsDespachosAtuaisEOsLinksPublicos() {
        when(tipoProcedimento.getCodigo()).thenReturn("CONSTITUICAO_CC");
        UUID despacho = documento("DESPACHO_CCDR_DEP", "despacho.pdf");

        DadosPedidoPcgt dados = montador.preparar(processo);
        PedidoAtualizacaoProcessoPcgt pedido = montador.montarAtualizacao(dados, urls);

        assertThat(dados.documentos()).extracting(DadosPedidoPcgt.Documento::id).containsExactly(despacho);
        assertThat(pedido.constituicaoComissaoConsultiva().titulo()).isEqualTo("Aviso n.º DR-2026-000001");
        assertThat(pedido.constituicaoComissaoConsultiva().url()).isEqualTo("https://dr.exemplo.pt/aviso/1-2026");
        assertThat(pedido.constituicaoComissaoConsultiva().ficheiros()).singleElement().satisfies(f -> {
            assertThat(f.nome()).isEqualTo("despacho.pdf");
            assertThat(f.url()).isEqualTo(URL + "token-despacho.pdf/despacho.pdf");
        });
    }

    @Test
    void deveMontarAAprovacaoEODepositoDoProcedimentoFinal() {
        when(tipoProcedimento.getCodigo()).thenReturn("ALTERACAO");
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(true);
        when(processo.getDadosFormulario()).thenReturn(comDataDaDeliberacao("2026-09-20"));
        when(processo.getNumeroDeposito()).thenReturn("03.11.11/PDM/02/2026/1");
        decisaoDeInicioPublicada(comDataDaDeliberacao("2026-02-16"));
        documento("DELIBERACAO_DEP", "aprovacao.pdf");
        documento("DELIBERACAO_CM_DEP", "camara.pdf");

        PedidoCriacaoProcessoPcgt pedido = montador.montarCriacao(montador.preparar(processo), urls).orElseThrow();

        assertThat(pedido.aprovacao().dataDeliberacaoAm()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(pedido.aprovacao().ficheirosAm()).extracting(f -> f.nome()).containsExactly("aprovacao.pdf");
        assertThat(pedido.aprovacao().dataDeliberacaoCmEim()).isEqualTo(LocalDate.of(2026, 2, 16));
        assertThat(pedido.aprovacao().ficheirosCmEim()).extracting(f -> f.url())
                .containsExactly(URL + "token-camara.pdf/camara.pdf");
        assertThat(pedido.deposito().numeroDeposito()).isEqualTo("03.11.11/PDM/02/2026/1");
        assertThat(pedido.deposito().dataDeposito()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(avisos()).isEmpty();
    }

    @Test
    void deveDeixarDeForaAParteCmComUmWarnSemDecisaoDeInicioPublicada() {
        when(tipoProcedimento.getCodigo()).thenReturn("ALTERACAO");
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(true);
        when(processo.getDadosFormulario()).thenReturn(comDataDaDeliberacao("2026-09-20"));
        when(processoRepository.findFirstByDinamicaAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByCriadoEmDesc(
                any(), any(), any())).thenReturn(Optional.empty());
        documento("DELIBERACAO_DEP", "aprovacao.pdf");
        documento("DELIBERACAO_CM_DEP", "camara.pdf");

        DadosPedidoPcgt dados = montador.preparar(processo);
        PedidoAtualizacaoProcessoPcgt pedido = montador.montarAtualizacao(dados, urls);

        assertThat(pedido.aprovacao().dataDeliberacaoCmEim()).isNull();
        assertThat(pedido.aprovacao().ficheirosCmEim()).isNull();
        assertThat(pedido.aprovacao().dataDeliberacaoAm()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(dados.documentos()).extracting(DadosPedidoPcgt.Documento::nome).containsExactly("aprovacao.pdf");
        assertThat(avisos()).singleElement().asString().contains("CM/EIM");
    }

    @Test
    void naoDeveUsarADeliberacaoDeAprovacaoForaDoProcedimentoFinalNemAParteAmSemData() {
        documento("DELIBERACAO_DEP", "inicio.pdf");
        assertThat(montador.montarAtualizacao(montador.preparar(processo), urls).aprovacao()).isNull();

        documento("DELIBERACAO_AM_DEP", "assembleia.pdf");
        assertThat(montador.montarAtualizacao(montador.preparar(processo), urls).aprovacao()).isNull();
        assertThat(avisos()).singleElement().asString().contains("parte AM");
    }
}
