package com.conkord.saigt.service.geoconform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.EntidadeGeoconform;
import com.conkord.saigt.integracao.geoconform.ErroGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoCriacaoProcessoGeoconform;
import com.conkord.saigt.integracao.geoconform.ProcessoGeoconform;
import com.conkord.saigt.integracao.geoconform.ResultadoGeoconform;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CriacaoProcessoGeoconformServiceTest {

    private static final UUID PROCESSO_ID = UUID.fromString("7b2c1d0e-4f3a-4b5c-9d6e-0f1a2b3c4d5e");
    private static final UUID DINAMICA_ID = UUID.fromString("1e2d3c4b-5a69-4788-9a0b-1c2d3e4f5a6b");
    private static final String IDUS = "01.1111.01.012399.02.90001.01.031.01";
    private static final String LOCATION = "/api/geoconform/v1/processos/" + IDUS;
    private static final String URL_ACESSO = "https://geoconform.exemplo.pt/acesso/" + IDUS;

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ClienteGeoconform cliente = mock(ClienteGeoconform.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final CriacaoProcessoGeoconformService servico = new CriacaoProcessoGeoconformService(processoRepository,
            idusRepository, dinamicaRepository, cliente, transacoes);

    private final Processo processo = mock(Processo.class);
    private final Dinamica dinamica = mock(Dinamica.class);
    private final TipoProcedimento tipoProcedimento = mock(TipoProcedimento.class);
    private final Entidade entidade = mock(Entidade.class);
    private final IdusProcesso idus = mock(IdusProcesso.class);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CriacaoProcessoGeoconformService.class);
    private final Logger loggerListener = (Logger) LoggerFactory.getLogger(CriacaoProcessoGeoconformListener.class);

    @BeforeEach
    void decisaoDeInicioPublicadaComGeoconform() {
        logs.start();
        logger.addAppender(logs);
        loggerListener.addAppender(logs);
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(processo));
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getEstado()).thenReturn(EstadoProcesso.PUBLICADO);
        when(processo.getNumeroProcesso()).thenReturn("00107");
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(procedimento.getTipoProcedimento()).thenReturn(tipoProcedimento);
        when(tipoProcedimento.getCodigo()).thenReturn("DECISAO_INICIO");
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getEntidade()).thenReturn(entidade);
        when(entidade.getDescricaoSaigt()).thenReturn("Câmara Municipal de Exemplo");
        when(entidade.getNipc()).thenReturn("500000000");
        when(dinamica.getId()).thenReturn(DINAMICA_ID);
        when(dinamica.getComGeoconform()).thenReturn(true);
        when(dinamica.designacao()).thenReturn("Plano Diretor Municipal de Exemplo");
        when(dinamica.getDescritivo()).thenReturn("Revisão do PDM");
        when(dinamicaRepository.findById(DINAMICA_ID)).thenReturn(Optional.of(dinamica));
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(idus));
        when(idus.getIdus()).thenReturn(IDUS);
    }

    @AfterEach
    void retirarAppender() {
        logger.detachAppender(logs);
        loggerListener.detachAppender(logs);
    }

    private void responder(ResultadoGeoconform<ProcessoGeoconform> resultado) {
        when(cliente.criarProcesso(any(), any())).thenReturn(resultado);
    }

    private static ResultadoGeoconform<ProcessoGeoconform> criado(String urlAcesso) {
        return new ResultadoGeoconform<>(new ResultadoChamadaExterna<>(201,
                new ProcessoGeoconform(IDUS, "00107", "aguarda_ativacao", urlAcesso), null, 12), null, LOCATION);
    }

    private static ResultadoGeoconform<ProcessoGeoconform> erro(FalhaTecnicaExterna falha, Integer status, String codigo) {
        return new ResultadoGeoconform<>(new ResultadoChamadaExterna<>(status, null, falha, 12),
                codigo == null ? null : new ErroGeoconform(null, null, status, codigo, null), null);
    }

    private List<ILoggingEvent> linhas(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).toList();
    }

    @Test
    void deveCriarComOIdusONumeroADesignacaoADinamicaAEntidadeEAFichaEGravarOUrlDeAcesso() {
        responder(criado(URL_ACESSO));

        servico.criarSeAplicavel(PROCESSO_ID);

        ArgumentCaptor<PedidoCriacaoProcessoGeoconform> pedido = ArgumentCaptor.forClass(PedidoCriacaoProcessoGeoconform.class);
        ArgumentCaptor<ReferenciaChamada> referencia = ArgumentCaptor.forClass(ReferenciaChamada.class);
        verify(cliente).criarProcesso(pedido.capture(), referencia.capture());
        assertThat(pedido.getValue()).isEqualTo(new PedidoCriacaoProcessoGeoconform(IDUS, "00107",
                "Plano Diretor Municipal de Exemplo", "Revisão do PDM",
                new EntidadeGeoconform("Câmara Municipal de Exemplo", "500000000"), true));
        assertThat(referencia.getValue()).isEqualTo(new ReferenciaChamada(PROCESSO_ID, IDUS, null));
        verify(dinamica).setUrlGeoconform(URL_ACESSO);
        assertThat(linhas(Level.INFO)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("Processo criado no GeoConform").contains("processoId=" + PROCESSO_ID)
                .contains("idus=" + IDUS).contains("location=" + LOCATION).contains("urlAcessoGravado=true")
                .doesNotContain(URL_ACESSO));
    }

    @Test
    void deveLerEGravarEmTransacoesNovasPorCorrerDepoisDoCommitDaPublicacao() {
        responder(criado(URL_ACESSO));

        servico.criarSeAplicavel(PROCESSO_ID);

        ArgumentCaptor<TransactionDefinition> definicoes = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transacoes, org.mockito.Mockito.times(2)).getTransaction(definicoes.capture());
        assertThat(definicoes.getAllValues()).extracting(TransactionDefinition::getPropagationBehavior)
                .containsOnly(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(definicoes.getAllValues().get(0).isReadOnly()).isTrue();
        assertThat(definicoes.getAllValues().get(1).isReadOnly()).isFalse();
    }

    @Test
    void deveOmitirONifQuandoAEntidadeNaoOTem() {
        when(entidade.getNipc()).thenReturn(" ");
        responder(criado(URL_ACESSO));

        servico.criarSeAplicavel(PROCESSO_ID);

        ArgumentCaptor<PedidoCriacaoProcessoGeoconform> pedido = ArgumentCaptor.forClass(PedidoCriacaoProcessoGeoconform.class);
        verify(cliente).criarProcesso(pedido.capture(), any());
        assertThat(pedido.getValue().entidade()).isEqualTo(new EntidadeGeoconform("Câmara Municipal de Exemplo", null));
    }

    @ParameterizedTest
    @ValueSource(booleans = false)
    void naoDeveChamarNumaDinamicaSemGeoconform(boolean comGeoconform) {
        when(dinamica.getComGeoconform()).thenReturn(comGeoconform);

        servico.criarSeAplicavel(PROCESSO_ID);

        verifyNoInteractions(cliente);
    }

    @Test
    void naoDeveChamarComAOpcaoGeoconformPorResponder() {
        when(dinamica.getComGeoconform()).thenReturn(null);

        servico.criarSeAplicavel(PROCESSO_ID);

        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSTITUICAO_CC", "DISCUSSAO_PUBLICA", "DELIBERACAO_AM"})
    void naoDeveChamarNaPublicacaoDeOutroProcedimento(String codigo) {
        when(tipoProcedimento.getCodigo()).thenReturn(codigo);

        servico.criarSeAplicavel(PROCESSO_ID);

        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void naoDeveChamarSeOProcessoJaNaoEstaPublicado(EstadoProcesso estado) {
        when(processo.getEstado()).thenReturn(estado);

        servico.criarSeAplicavel(PROCESSO_ID);

        verifyNoInteractions(cliente);
    }

    @Test
    void naoDeveChamarSemIdusERegistaUmErrorComOIdDoProcesso() {
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.empty());

        servico.criarSeAplicavel(PROCESSO_ID);

        verifyNoInteractions(cliente);
        assertThat(linhas(Level.ERROR)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("sem IDUS").contains("processoId=" + PROCESSO_ID));
    }

    @Test
    void deveContarO409IduDuplicadoComoCriadoSemMudarADinamica() {
        responder(erro(FalhaTecnicaExterna.ERRO_HTTP, 409, "idu_duplicado"));

        servico.criarSeAplicavel(PROCESSO_ID);

        verify(dinamica, never()).setUrlGeoconform(anyString());
        assertThat(linhas(Level.ERROR)).isEmpty();
        assertThat(linhas(Level.INFO)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("Processo criado no GeoConform").contains("processoId=" + PROCESSO_ID)
                .contains("httpStatus=409").contains("urlAcessoGravado=false"));
    }

    @Test
    void deveRegistarUmErrorComOTipoDeFalhaSemMudarADinamicaNemOProcesso() {
        responder(erro(FalhaTecnicaExterna.ERRO_HTTP, 422, "payload_invalido"));

        servico.criarSeAplicavel(PROCESSO_ID);

        verify(dinamica, never()).setUrlGeoconform(anyString());
        verify(processo, never()).setEstado(any());
        verify(processoRepository, never()).save(any());
        verify(dinamicaRepository, never()).save(any());
        assertThat(linhas(Level.ERROR)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("processoId=" + PROCESSO_ID).contains("falha=ERRO_HTTP").contains("httpStatus=422")
                .contains("codigo=payload_invalido"));
    }

    @Test
    void deveRegistarUmErrorNoTimeoutSemNovaTentativa() {
        responder(erro(FalhaTecnicaExterna.TIMEOUT, null, null));

        servico.criarSeAplicavel(PROCESSO_ID);

        verify(cliente).criarProcesso(any(), any());
        verify(dinamica, never()).setUrlGeoconform(anyString());
        assertThat(linhas(Level.ERROR)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("falha=TIMEOUT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://geoconform.exemplo.pt/acesso", "/acesso/relativo", " "})
    void naoDeveGravarUmUrlDeAcessoQueNaoSejaHttpAbsoluto(String urlAcesso) {
        responder(criado(urlAcesso));

        servico.criarSeAplicavel(PROCESSO_ID);

        verify(dinamica, never()).setUrlGeoconform(anyString());
        assertThat(linhas(Level.WARN)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("sem urlAcesso http(s) válido").doesNotContain(urlAcesso.isBlank() ? "§" : urlAcesso));
        assertThat(linhas(Level.INFO)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("urlAcessoGravado=false"));
    }

    @Test
    void ignoraAsOutrasTransicoesNoListener() {
        CriacaoProcessoGeoconformService servicoSimulado = mock(CriacaoProcessoGeoconformService.class);
        CriacaoProcessoGeoconformListener listener = new CriacaoProcessoGeoconformListener(servicoSimulado);

        listener.aoTransitar(ProcessoTransitouEvento.de(PROCESSO_ID, EstadoProcesso.EM_VALIDACAO_DGT,
                EstadoProcesso.VALIDADO_DGT));
        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoIncm(PROCESSO_ID, EstadoProcesso.EM_VALIDACAO_INCM,
                List.of("recusado")));
        verifyNoInteractions(servicoSimulado);

        listener.aoTransitar(ProcessoTransitouEvento.de(PROCESSO_ID, EstadoProcesso.EM_VALIDACAO_INCM,
                EstadoProcesso.PUBLICADO));
        verify(servicoSimulado).criarSeAplicavel(PROCESSO_ID);
    }

    @Test
    void umaExcecaoNaCriacaoFicaNoLogENaoSaiDoListener() {
        CriacaoProcessoGeoconformService servicoSimulado = mock(CriacaoProcessoGeoconformService.class);
        doThrow(new IllegalStateException("base de dados indisponível")).when(servicoSimulado)
                .criarSeAplicavel(eq(PROCESSO_ID));
        CriacaoProcessoGeoconformListener listener = new CriacaoProcessoGeoconformListener(servicoSimulado);

        assertThatCode(() -> listener.aoTransitar(ProcessoTransitouEvento.de(PROCESSO_ID,
                EstadoProcesso.EM_VALIDACAO_INCM, EstadoProcesso.PUBLICADO))).doesNotThrowAnyException();

        assertThat(linhas(Level.ERROR)).singleElement().satisfies(linha -> assertThat(linha.getFormattedMessage())
                .contains("processoId=" + PROCESSO_ID).contains("IllegalStateException"));
    }

    @Test
    void aceitaSoUrlsHttpEHttpsAbsolutos() {
        assertThat(CriacaoProcessoGeoconformService.urlAceitavel("https://gc.exemplo.pt/acesso/1")).isTrue();
        assertThat(CriacaoProcessoGeoconformService.urlAceitavel("HTTP://localhost:8092/acesso/1")).isTrue();
        assertThat(CriacaoProcessoGeoconformService.urlAceitavel(null)).isFalse();
        assertThat(CriacaoProcessoGeoconformService.urlAceitavel("https://" + "a".repeat(2050))).isFalse();
        assertThat(CriacaoProcessoGeoconformService.urlAceitavel("https:///sem-host")).isFalse();
    }
}
