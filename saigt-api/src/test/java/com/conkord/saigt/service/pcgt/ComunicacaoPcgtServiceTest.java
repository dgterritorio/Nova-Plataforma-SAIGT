package com.conkord.saigt.service.pcgt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.exception.LinkPublicoNaoPermitidoException;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.pcgt.ClientePcgt;
import com.conkord.saigt.integracao.pcgt.ErroPcgt;
import com.conkord.saigt.integracao.pcgt.PedidoAtualizacaoProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.PedidoCriacaoProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.ProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.ResultadoPcgt;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
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
import com.conkord.saigt.service.documento.LinkPublicoService;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.conkord.saigt.service.notificacao.AvisoPedidoPcgtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComunicacaoPcgtServiceTest {

    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final UUID INSTRUMENTO_ID = UUID.randomUUID();
    private static final String IDUS = "01.1111.01.000123.02.00042.01.031.01.20261024";
    private static final String URL_LINK = "https://saigt.exemplo.pt/api/v1/publico/7f1c2e3d-token-secreto/despacho.pdf";

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final InstrumentoConcelhoRepository concelhoRepository = mock(InstrumentoConcelhoRepository.class);
    private final ProcessoDocumentoRepository documentoRepository = mock(ProcessoDocumentoRepository.class);
    private final NumeroDepositoService numeroDepositoService = mock(NumeroDepositoService.class);
    private final LinkPublicoService linkPublicoService = mock(LinkPublicoService.class);
    private final ClientePcgt clientePcgt = mock(ClientePcgt.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final AvisoPedidoPcgtService avisoPedidoPcgt = mock(AvisoPedidoPcgtService.class);
    private final ComunicacaoPcgtService servico = new ComunicacaoPcgtService(processoRepository,
            new MontadorPedidoPcgt(idusRepository, concelhoRepository, documentoRepository, processoRepository,
                    numeroDepositoService, new ObjectMapper()),
            numeroDepositoService, linkPublicoService, clientePcgt, avisoPedidoPcgt, transacoes);

    private final Processo processo = mock(Processo.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final TipoDinamica tipoDinamica = mock(TipoDinamica.class);
    private final TipoProcedimento tipoProcedimento = mock(TipoProcedimento.class);
    private final List<ProcessoDocumento> documentos = new ArrayList<>();

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ComunicacaoPcgtService.class);
    private final Logger loggerMontador = (Logger) LoggerFactory.getLogger(MontadorPedidoPcgt.class);

    @BeforeEach
    void decisaoDeInicioPublicadaDeUmaAlteracaoDePdm() {
        logs.start();
        logger.addAppender(logs);
        loggerMontador.addAppender(logs);
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        Dinamica dinamica = mock(Dinamica.class);
        Instrumento instrumento = mock(Instrumento.class);
        InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        TipoAto tipoAto = mock(TipoAto.class);
        Entidade entidade = mock(Entidade.class);
        IdusProcesso idus = mock(IdusProcesso.class);
        when(instrumento.getId()).thenReturn(INSTRUMENTO_ID);
        when(instrumento.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(instrumento.getDesignacao()).thenReturn("Plano Diretor Municipal de Sintra");
        when(processo.getEntidade()).thenReturn(entidade);
        when(entidade.getCodigo()).thenReturn("CM-SINTRA");
        when(tipoInstrumento.getCodigo()).thenReturn("PDM");
        when(tipoInstrumento.getCategoria()).thenReturn("PLANO");
        when(tipoInstrumento.getPcgtCodigo()).thenReturn("01");
        when(tipoDinamica.getNome()).thenReturn("Alteração");
        when(tipoDinamica.getPcgtCodigo()).thenReturn("02");
        when(dinamicaConfig.getTipoDinamica()).thenReturn(tipoDinamica);
        when(dinamica.getInstrumento()).thenReturn(instrumento);
        when(dinamica.instrumentoDeInicio()).thenReturn(Optional.of(instrumento));
        when(dinamica.designacao()).thenReturn("Plano Diretor Municipal de Sintra");
        when(dinamicaConfig.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        when(dinamica.getNumeroDinamica()).thenReturn("00077");
        when(procedimento.getTipoProcedimento()).thenReturn(tipoProcedimento);
        when(tipoProcedimento.getCodigo()).thenReturn("DECISAO_INICIO");
        when(tipoAto.getNome()).thenReturn("Aviso");
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getNumeroProcesso()).thenReturn("00107");
        when(processo.getEstado()).thenReturn(EstadoProcesso.PUBLICADO);
        when(processo.getOrigemDados()).thenReturn("NATIVO");
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(processo.getTipoAto()).thenReturn(tipoAto);
        when(processo.getNumeroDr()).thenReturn("DR-2026-000001");
        when(processo.getDataPublicacaoDr()).thenReturn(LocalDate.of(2026, 10, 24));
        when(processo.getLinkDr()).thenReturn("https://dr.exemplo.pt/aviso/1-2026");
        when(idus.getIdus()).thenReturn(IDUS);
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(idus));
        List<InstrumentoConcelho> concelhos = List.of(new InstrumentoConcelho(instrumento, "1111"));
        when(concelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(INSTRUMENTO_ID)).thenReturn(concelhos);
        when(documentoRepository.findAtuaisDoProcessoPorCodigos(eq(PROCESSO_ID), anyCollection())).thenReturn(documentos);
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(processo));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        loggerMontador.detachAppender(logs);
    }

    private static ResultadoPcgt<ProcessoPcgt> resposta(int status) {
        return new ResultadoPcgt<>(new ResultadoChamadaExterna<>(status, null, null, 5), null);
    }

    private static ResultadoPcgt<ProcessoPcgt> erro(int status, String mensagem) {
        return new ResultadoPcgt<>(new ResultadoChamadaExterna<>(status, null, FalhaTecnicaExterna.ERRO_HTTP, 5),
                mensagem == null ? null : new ErroPcgt(status, mensagem));
    }

    private static ResultadoPcgt<ProcessoPcgt> falhaTecnica(FalhaTecnicaExterna falha) {
        return new ResultadoPcgt<>(new ResultadoChamadaExterna<>(null, null, falha, 5), null);
    }

    private UUID despachoDaComissaoConsultiva() {
        when(tipoProcedimento.getCodigo()).thenReturn("CONSTITUICAO_CC");
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getCodigo()).thenReturn("DESPACHO_CCDR_DEP");
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        UUID id = UUID.randomUUID();
        when(documento.getId()).thenReturn(id);
        when(documento.getNomeFicheiro()).thenReturn("despacho.pdf");
        when(documento.getDocumentoConfig()).thenReturn(item);
        documentos.add(documento);
        return id;
    }

    private List<String> linhas(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void deveAtualizarOProcessoDaDinamicaSemCriarQuandoAPcgtResponde200() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(resposta(200));

        servico.comunicarPublicacao(PROCESSO_ID);

        ArgumentCaptor<PedidoAtualizacaoProcessoPcgt> pedido = ArgumentCaptor.forClass(PedidoAtualizacaoProcessoPcgt.class);
        verify(clientePcgt).atualizar(pedido.capture(), eq(new ReferenciaChamada(PROCESSO_ID, IDUS, null)));
        assertThat(pedido.getValue().codigoProcesso()).isEqualTo("00077");
        verify(clientePcgt, never()).criar(any(), any());
        assertThat(linhas(Level.INFO)).anyMatch(l -> l.contains("Processo PCGT atualizado"));
    }

    @Test
    void deveCriarComOMesmoCodigoQuandoOPutResponde404() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, "Processo não encontrado para o codigoProcesso: 00077"));
        when(clientePcgt.criar(any(), any())).thenReturn(resposta(201));

        servico.comunicarPublicacao(PROCESSO_ID);

        ArgumentCaptor<PedidoCriacaoProcessoPcgt> pedido = ArgumentCaptor.forClass(PedidoCriacaoProcessoPcgt.class);
        verify(clientePcgt).criar(pedido.capture(), any());
        assertThat(pedido.getValue().codigoProcesso()).isEqualTo("00077");
        assertThat(pedido.getValue().designacao()).isEqualTo("Alteração — Plano Diretor Municipal de Sintra");
        assertThat(pedido.getValue().tipoInstrumentoCodigo()).isEqualTo("01");
        assertThat(pedido.getValue().tipoProcedimentoCodigo()).isEqualTo("02");
        assertThat(pedido.getValue().entidade().codigoEntidade()).isEqualTo("CM-SINTRA");
        assertThat(pedido.getValue().municipioToken()).isEqualTo("1111");
        assertThat(pedido.getValue().codigoIdus()).isEqualTo(IDUS);
        assertThat(pedido.getValue().tipoAto()).isEqualTo("Aviso");
        assertThat(pedido.getValue().numeroAto()).isEqualTo("DR-2026-000001");
        assertThat(linhas(Level.INFO)).anyMatch(l -> l.contains("Processo PCGT criado"));
        assertThat(linhas(Level.WARN)).isEmpty();
    }

    @Test
    void naoDeveCriarComUmWarnQuandoOTipoDeDinamicaNaoTemCodigoPcgt() {
        when(tipoDinamica.getPcgtCodigo()).thenReturn(null);
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, null));

        servico.comunicarPublicacao(PROCESSO_ID);

        verify(clientePcgt, never()).criar(any(), any());
        assertThat(linhas(Level.WARN)).singleElement().asString().contains("tipo_dinamica.pcgt_codigo");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void naoDeveTentarCriarNemRepetirQuandoOPutFalha(int status) {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(status, "recusado"));

        servico.comunicarPublicacao(PROCESSO_ID);

        verify(clientePcgt).atualizar(any(), any());
        verify(clientePcgt, never()).criar(any(), any());
        verify(processo, never()).setEstado(any());
        assertThat(linhas(Level.WARN)).singleElement().asString().contains("passo=atualizar")
                .contains("httpStatus=" + status).contains("sistema=PCGT").contains("numeroDinamica=00077");
    }

    @Test
    void naoDeveTentarCriarQuandoOPutDaTimeout() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(falhaTecnica(FalhaTecnicaExterna.TIMEOUT));

        servico.comunicarPublicacao(PROCESSO_ID);

        verify(clientePcgt, never()).criar(any(), any());
        assertThat(linhas(Level.WARN)).singleElement().asString().contains("passo=atualizar").contains("falha=TIMEOUT");
    }

    @Test
    void deveAvisarComOPassoCriarOCodigoEAMensagemQuandoOPostDa400() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, null));
        when(clientePcgt.criar(any(), any())).thenReturn(erro(400, "designacao em falta"));

        servico.comunicarPublicacao(PROCESSO_ID);

        assertThat(linhas(Level.WARN)).singleElement().asString().contains("passo=criar").contains("httpStatus=400")
                .contains("code=400").contains("message=designacao em falta");
    }

    @Test
    void naoDeveComunicarUmProcessoDeRegistoExterno() {
        when(processo.getOrigemDados()).thenReturn("REGISTO_EXTERNO");

        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(clientePcgt, linkPublicoService);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void naoDeveComunicarAReneDeixaUmaLinhaInfo() {
        when(tipoInstrumento.getCodigo()).thenReturn("REN");

        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(clientePcgt);
        assertThat(linhas(Level.INFO)).singleElement().asString().contains("REN não comunicada à PCGT");
    }

    @Test
    void deveComunicarUmaSrupSoNoProcedimentoFinal() {
        when(tipoInstrumento.getCategoria()).thenReturn("SRUP");
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(false);

        servico.comunicarPublicacao(PROCESSO_ID);
        verifyNoInteractions(clientePcgt);

        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(true);
        when(clientePcgt.atualizar(any(), any())).thenReturn(resposta(200));
        servico.comunicarPublicacao(PROCESSO_ID);
        verify(clientePcgt).atualizar(any(), any());
    }

    @Test
    void naoDeveComunicarUmProcessoQueNaoEstaPublicado() {
        when(processo.getEstado()).thenReturn(EstadoProcesso.EM_VALIDACAO_INCM);

        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(clientePcgt);
    }

    @Test
    void deveCriarOLinkPublicoPcgtDeCadaDocumentoAntesDoPedidoEUsarOSeuUrl() {
        UUID despacho = despachoDaComissaoConsultiva();
        when(linkPublicoService.obterOuCriar(despacho, DestinoLinkPublico.PCGT))
                .thenReturn(new LinkPublicoService.LinkPublico(UUID.randomUUID(), despacho, URL_LINK));
        when(clientePcgt.atualizar(any(), any())).thenReturn(resposta(200));

        servico.comunicarPublicacao(PROCESSO_ID);

        var ordem = inOrder(linkPublicoService, clientePcgt);
        ordem.verify(linkPublicoService).obterOuCriar(despacho, DestinoLinkPublico.PCGT);
        ArgumentCaptor<PedidoAtualizacaoProcessoPcgt> pedido = ArgumentCaptor.forClass(PedidoAtualizacaoProcessoPcgt.class);
        ordem.verify(clientePcgt).atualizar(pedido.capture(), any());
        assertThat(pedido.getValue().constituicaoComissaoConsultiva().ficheiros()).singleElement()
                .satisfies(f -> assertThat(f.url()).isEqualTo(URL_LINK));
    }

    @Test
    void naoDeveEnviarNadaComUmWarnQuandoUmDocumentoNaoTemLinkPublicoPermitido() {
        UUID despacho = despachoDaComissaoConsultiva();
        when(linkPublicoService.obterOuCriar(despacho, DestinoLinkPublico.PCGT))
                .thenThrow(new LinkPublicoNaoPermitidoException("despacho.pdf"));

        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(clientePcgt);
        assertThat(linhas(Level.WARN)).singleElement().asString().contains("processoDocumentoId=" + despacho);
    }

    @Test
    void naoDeveEscreverNoLogTokensDeLinkPublicoSemMascara() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, null));
        when(clientePcgt.criar(any(), any())).thenReturn(erro(400, "Ficheiro inválido: " + URL_LINK));

        servico.comunicarPublicacao(PROCESSO_ID);

        assertThat(linhas(Level.WARN)).singleElement().asString().contains("/api/v1/publico/***")
                .doesNotContain("token-secreto");
    }

    @Test
    void devePedirOAvisoDePedidoEnviadoDepoisDoPutComSucesso() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(resposta(200));

        servico.comunicarPublicacao(PROCESSO_ID);

        var ordem = inOrder(clientePcgt, avisoPedidoPcgt);
        ordem.verify(clientePcgt).atualizar(any(), any());
        ordem.verify(avisoPedidoPcgt).avisarPedidoEnviado(PROCESSO_ID);
    }

    @Test
    void devePedirOAvisoDePedidoEnviadoDepoisDoPostComSucesso() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, null));
        when(clientePcgt.criar(any(), any())).thenReturn(resposta(201));

        servico.comunicarPublicacao(PROCESSO_ID);

        var ordem = inOrder(clientePcgt, avisoPedidoPcgt);
        ordem.verify(clientePcgt).criar(any(), any());
        ordem.verify(avisoPedidoPcgt).avisarPedidoEnviado(PROCESSO_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void naoDevePedirOAvisoQuandoOPutFalha(int status) {
        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(status, "recusado"));

        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(avisoPedidoPcgt);
    }

    @Test
    void naoDevePedirOAvisoQuandoOPutDaTimeoutOuOPostFalha() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(falhaTecnica(FalhaTecnicaExterna.TIMEOUT));
        servico.comunicarPublicacao(PROCESSO_ID);

        when(clientePcgt.atualizar(any(), any())).thenReturn(erro(404, null));
        when(clientePcgt.criar(any(), any())).thenReturn(erro(400, "designacao em falta"));
        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(avisoPedidoPcgt);
    }

    @Test
    void naoDevePedirOAvisoNumProcessoDeRegistoExternoNemNaRen() {
        when(processo.getOrigemDados()).thenReturn("REGISTO_EXTERNO");
        servico.comunicarPublicacao(PROCESSO_ID);

        when(processo.getOrigemDados()).thenReturn("NATIVO");
        when(tipoInstrumento.getCodigo()).thenReturn("REN");
        servico.comunicarPublicacao(PROCESSO_ID);

        verifyNoInteractions(avisoPedidoPcgt);
    }

    @Test
    void umaFalhaAoCriarOAvisoFicaNoLogSemDesfazerAComunicacaoNemMexerNoProcesso() {
        when(clientePcgt.atualizar(any(), any())).thenReturn(resposta(200));
        doThrow(new IllegalStateException("base de dados indisponível"))
                .when(avisoPedidoPcgt).avisarPedidoEnviado(PROCESSO_ID);

        servico.comunicarPublicacao(PROCESSO_ID);

        verify(clientePcgt).atualizar(any(), any());
        verify(clientePcgt, never()).criar(any(), any());
        verify(processo, never()).setEstado(any());
        assertThat(linhas(Level.INFO)).anyMatch(l -> l.contains("Processo PCGT atualizado"));
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.ERROR).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).contains("aviso de pedido enviado para a PCGT")
                    .contains("processoId=" + PROCESSO_ID);
            assertThat(e.getThrowableProxy()).isNotNull();
        });
    }
}
