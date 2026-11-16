package com.conkord.saigt.service.geoconform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoVisibilidadeGeoconform;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.AlertaCaducidadeDinamicaEvento;
import com.conkord.saigt.service.instrumento.LimiarCaducidade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VisibilidadeGeoconformServiceTest {

    private static final UUID DINAMICA_ID = UUID.fromString("2f3e4d5c-6b7a-4899-8a0b-1c2d3e4f5a6b");
    private static final UUID PROCESSO_ID = UUID.fromString("8c3d2e1f-5a4b-4c6d-9e7f-0a1b2c3d4e5f");
    private static final String IDUS = "01.1199.01.012345.02.90001.01.031.01";
    private static final LocalDate DATA_CADUCIDADE = LocalDate.of(2026, 11, 12);
    private static final AlertaCaducidadeDinamicaEvento CADUCOU =
            new AlertaCaducidadeDinamicaEvento(DINAMICA_ID, LimiarCaducidade.DATA, DATA_CADUCIDADE);

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final ClienteGeoconform cliente = mock(ClienteGeoconform.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final VisibilidadeGeoconformService servico = new VisibilidadeGeoconformService(dinamicaRepository,
            processoRepository, idusRepository, cliente, transacoes);
    private final VisibilidadeGeoconformListener listener = new VisibilidadeGeoconformListener(servico);

    private final Dinamica dinamica = mock(Dinamica.class);
    private final Processo decisaoInicio = mock(Processo.class);
    private final IdusProcesso idus = mock(IdusProcesso.class);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(VisibilidadeGeoconformService.class);
    private final Logger loggerListener = (Logger) LoggerFactory.getLogger(VisibilidadeGeoconformListener.class);

    @BeforeEach
    void dinamicaComGeoconformEDecisaoDeInicioPublicadaComIdus() {
        logs.start();
        logger.addAppender(logs);
        loggerListener.addAppender(logs);
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(dinamicaRepository.findById(DINAMICA_ID)).thenReturn(Optional.of(dinamica));
        when(dinamica.getId()).thenReturn(DINAMICA_ID);
        when(dinamica.getComGeoconform()).thenReturn(true);
        when(processoRepository
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
                        DINAMICA_ID, "DECISAO_INICIO", EstadoProcesso.PUBLICADO)).thenReturn(Optional.of(decisaoInicio));
        when(decisaoInicio.getId()).thenReturn(PROCESSO_ID);
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(idus));
        when(idus.getIdus()).thenReturn(IDUS);
        responder(new ResultadoChamadaExterna<>(200, null, null, 9));
    }

    @AfterEach
    void retirarAppender() {
        logger.detachAppender(logs);
        loggerListener.detachAppender(logs);
    }

    private void responder(ResultadoChamadaExterna<Void> resultado) {
        when(cliente.alterarVisibilidade(any(), any(), any())).thenReturn(resultado);
    }

    private PedidoVisibilidadeGeoconform pedidoEnviado() {
        ArgumentCaptor<PedidoVisibilidadeGeoconform> pedido = ArgumentCaptor.forClass(PedidoVisibilidadeGeoconform.class);
        verify(cliente).alterarVisibilidade(eq(IDUS), pedido.capture(), any());
        return pedido.getValue();
    }

    private List<String> linhas(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void deveRetirarComVisivelFalseMotivoCaducidadeEADataDeCaducidadeQuandoADinamicaCaduca() {
        listener.aoCaducar(CADUCOU);

        ArgumentCaptor<ReferenciaChamada> referencia = ArgumentCaptor.forClass(ReferenciaChamada.class);
        verify(cliente).alterarVisibilidade(eq(IDUS), eq(new PedidoVisibilidadeGeoconform(false, "caducidade",
                DATA_CADUCIDADE)), referencia.capture());
        assertThat(referencia.getValue()).isEqualTo(new ReferenciaChamada(PROCESSO_ID, IDUS, null));
        assertThat(linhas(Level.INFO)).singleElement().asString()
                .contains("dinamicaId=" + DINAMICA_ID).contains("idus=" + IDUS).contains("visivel=false");
        assertThat(linhas(Level.WARN)).isEmpty();
        assertThat(linhas(Level.ERROR)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = LimiarCaducidade.class, names = {"UM_MES", "QUINZE_DIAS"})
    void naoDeveMexerNoGeoconformNosAvisosDeUmMesEQuinzeDias(LimiarCaducidade limiar) {
        listener.aoCaducar(new AlertaCaducidadeDinamicaEvento(DINAMICA_ID, limiar, DATA_CADUCIDADE));

        verifyNoInteractions(cliente, dinamicaRepository, processoRepository, idusRepository);
        assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void naoDeveEnviarPedidoNumaDinamicaSemGeoconform(Boolean comGeoconform) {
        when(dinamica.getComGeoconform()).thenReturn(comGeoconform);

        listener.aoCaducar(CADUCOU);
        servico.repor(DINAMICA_ID);

        verifyNoInteractions(cliente, processoRepository, idusRepository);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void naoDeveEnviarPedidoSemDecisaoDeInicioPublicadaEDeveDeixarUmWarnComODinamicaId() {
        when(processoRepository
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
                        any(), any(), any())).thenReturn(Optional.empty());

        listener.aoCaducar(CADUCOU);

        verifyNoInteractions(cliente);
        assertThat(linhas(Level.WARN)).singleElement().asString()
                .contains("sem Decisão de início publicada").contains("dinamicaId=" + DINAMICA_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @NullSource
    void naoDeveEnviarPedidoComADecisaoDeInicioSemIdusEDeveDeixarUmError(String valor) {
        if (valor == null) {
            when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.empty());
        } else {
            when(idus.getIdus()).thenReturn(valor);
        }

        listener.aoCaducar(CADUCOU);

        verifyNoInteractions(cliente);
        assertThat(linhas(Level.ERROR)).singleElement().asString()
                .contains("sem IDUS").contains("dinamicaId=" + DINAMICA_ID).contains("processoId=" + PROCESSO_ID);
    }

    @Test
    void deveDeixarUmWarnQuandoOGeoconformResponde404() {
        responder(new ResultadoChamadaExterna<>(404, null, FalhaTecnicaExterna.ERRO_HTTP, 9));

        listener.aoCaducar(CADUCOU);

        assertThat(linhas(Level.WARN)).singleElement().asString()
                .contains("desconhecido do GeoConform").contains("dinamicaId=" + DINAMICA_ID).contains("idus=" + IDUS);
        assertThat(linhas(Level.ERROR)).isEmpty();
        assertThat(linhas(Level.INFO)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = FalhaTecnicaExterna.class, names = {"ERRO_HTTP", "TIMEOUT", "ERRO_REDE", "CIRCUITO_ABERTO",
            "NAO_CONFIGURADO"})
    void deveDeixarUmErrorComOTipoDeFalhaSemNovaTentativaNasOutrasFalhas(FalhaTecnicaExterna falha) {
        Integer status = falha == FalhaTecnicaExterna.ERRO_HTTP ? 503 : null;
        responder(new ResultadoChamadaExterna<>(status, null, falha, 9));

        listener.aoCaducar(CADUCOU);

        verify(cliente, times(1)).alterarVisibilidade(any(), any(), any());
        assertThat(linhas(Level.ERROR)).singleElement().asString()
                .contains("falha=" + falha).contains("httpStatus=" + status).contains("dinamicaId=" + DINAMICA_ID);
        assertThat(linhas(Level.INFO)).isEmpty();
    }

    @Test
    void naoDeveAlterarADinamicaNemPropagarQuandoAChamadaFalhaOuRebenta() {
        responder(new ResultadoChamadaExterna<>(503, null, FalhaTecnicaExterna.ERRO_HTTP, 9));
        listener.aoCaducar(CADUCOU);
        when(cliente.alterarVisibilidade(any(), any(), any())).thenThrow(new IllegalStateException("inesperado"));

        assertThatCode(() -> listener.aoCaducar(CADUCOU)).doesNotThrowAnyException();

        verify(dinamica, never()).setEstado(any());
        verify(dinamicaRepository, never()).save(any());
        verify(dinamicaRepository, never()).marcarCaducada(any());
        assertThat(linhas(Level.ERROR)).hasSize(2).last().asString()
                .contains("dinamicaId=" + DINAMICA_ID).contains("IllegalStateException");
    }

    @Test
    void deveLerNumaTransacaoNovaSoDeLeituraEChamarOGeoconformDepoisDeAFechar() {
        listener.aoCaducar(CADUCOU);

        ArgumentCaptor<TransactionDefinition> definicao = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transacoes).getTransaction(definicao.capture());
        assertThat(definicao.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(definicao.getValue().isReadOnly()).isTrue();
        var ordem = org.mockito.Mockito.inOrder(transacoes, cliente);
        ordem.verify(transacoes).commit(any());
        ordem.verify(cliente).alterarVisibilidade(any(), any(), any());
    }

    @Test
    void reporDeveEnviarSoVisivelTrueComUmInfoSemAlterarADinamica() {
        servico.repor(DINAMICA_ID);

        assertThat(pedidoEnviado()).isEqualTo(new PedidoVisibilidadeGeoconform(true, null, null));
        assertThat(linhas(Level.INFO)).singleElement().asString()
                .contains("acao=repor").contains("visivel=true").contains("dinamicaId=" + DINAMICA_ID)
                .contains("idus=" + IDUS);
        verify(dinamica, never()).setEstado(any());
        verify(dinamicaRepository, never()).save(any());
    }

    @Test
    void reporSemDecisaoDeInicioPublicadaDeveDeixarUmWarnSemPedido() {
        when(processoRepository
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
                        any(), any(), any())).thenReturn(Optional.empty());

        servico.repor(DINAMICA_ID);

        verifyNoInteractions(cliente);
        assertThat(linhas(Level.WARN)).singleElement().asString().contains("acao=repor")
                .contains("dinamicaId=" + DINAMICA_ID);
    }

    @Test
    void reporSemIdusDeveDeixarUmErrorSemPedido() {
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.empty());

        servico.repor(DINAMICA_ID);

        verifyNoInteractions(cliente);
        assertThat(linhas(Level.ERROR)).singleElement().asString().contains("acao=repor").contains("sem IDUS");
    }

    @Test
    void reporDeveTratarO404ComWarnEAsOutrasFalhasComError() {
        responder(new ResultadoChamadaExterna<>(404, null, FalhaTecnicaExterna.ERRO_HTTP, 9));
        servico.repor(DINAMICA_ID);
        responder(new ResultadoChamadaExterna<>(502, null, FalhaTecnicaExterna.ERRO_HTTP, 9));
        servico.repor(DINAMICA_ID);
        responder(new ResultadoChamadaExterna<>(null, null, FalhaTecnicaExterna.TIMEOUT, 9));
        servico.repor(DINAMICA_ID);

        assertThat(linhas(Level.WARN)).singleElement().asString().contains("acao=repor");
        assertThat(linhas(Level.ERROR)).hasSize(2).allSatisfy(l -> assertThat(l).contains("acao=repor"));
        assertThat(linhas(Level.ERROR).get(0)).contains("httpStatus=502");
        assertThat(linhas(Level.ERROR).get(1)).contains("falha=TIMEOUT");
    }

    @Test
    void deveEscolherADecisaoDeInicioPublicadaDeOcorrenciaMaisAltaPelaConsultaDoRepositorio() {
        listener.aoCaducar(CADUCOU);

        verify(processoRepository)
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
                        DINAMICA_ID, "DECISAO_INICIO", EstadoProcesso.PUBLICADO);
    }
}
