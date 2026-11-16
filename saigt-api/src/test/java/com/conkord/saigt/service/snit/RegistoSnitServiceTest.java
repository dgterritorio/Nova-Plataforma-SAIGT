package com.conkord.saigt.service.snit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.FicheirosRecebidosSnit;
import com.conkord.saigt.integracao.snit.PublicacaoDrSnit;
import com.conkord.saigt.integracao.snit.PublicacaoRecebidaSnit;
import com.conkord.saigt.integracao.snit.PublicacaoSnit;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistoSnitServiceTest {

    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final UUID MANIFESTO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-10T10:00:00Z");
    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(PROCESSO_ID, null, null);

    private static final PublicacaoSnit PUBLICACAO = new PublicacaoSnit(42L, 6, 1, 3,
            "Plano Diretor Municipal de Sintra", "PT1B", "1111", null, "00",
            new PublicacaoDrSnit("Aviso", "2", "206", LocalDate.of(2026, 10, 24), "https://dr.exemplo.pt/1"), null);
    private static final DepositoSnit DEPOSITO = new DepositoSnit(42L, "03.11.11/PDM/01/2026/1", AGORA, null,
            "https://saigt.exemplo.pt/api/v1/integracoes/snit/depositos/00042", null);
    private static final List<FicheiroDepositoSnit> FICHEIROS = List.of(new FicheiroDepositoSnit(
            UUID.randomUUID().toString(), "Planta.zip", null, "https://saigt.exemplo.pt/f", true, AGORA));

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final NumeroDepositoService numeroDepositoService = mock(NumeroDepositoService.class);
    private final MontadorPedidoSnit montador = mock(MontadorPedidoSnit.class);
    private final ClienteSnit clienteSnit = mock(ClienteSnit.class);
    private final ManifestoDepositoSnitService manifestoService = mock(ManifestoDepositoSnitService.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final RegistoSnitService service = new RegistoSnitService(processoRepository, numeroDepositoService,
            montador, clienteSnit, manifestoService, idusRepository, mock(PlatformTransactionManager.class),
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private final Processo processo = mock(Processo.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RegistoSnitService.class);

    @BeforeEach
    void procedimentoFinalPublicadoComPedidosMontados() {
        logs.start();
        logger.addAppender(logs);
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getNumeroProcesso()).thenReturn("00042");
        when(processo.getEstado()).thenReturn(EstadoProcesso.PUBLICADO);
        when(processo.getOrigemDados()).thenReturn("NATIVO");
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(processo));
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(true);
        when(montador.montarPublicacao(processo)).thenReturn(Optional.of(PUBLICACAO));
        when(montador.montarDeposito(processo, AGORA)).thenReturn(Optional.of(DEPOSITO));
        when(montador.montarFicheiros(processo)).thenReturn(FICHEIROS);
        when(clienteSnit.registarPublicacao(PUBLICACAO, REFERENCIA)).thenReturn(publicacao(201, null));
        when(manifestoService.comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, REFERENCIA))
                .thenReturn(new ComunicacaoDepositoSnit(MANIFESTO_ID, deposito(201, null), ficheiros(201, null)));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void deveRegistarAPublicacaoEDepoisODepositoComOsFicheiros() {
        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        InOrder ordem = inOrder(clienteSnit, manifestoService);
        ordem.verify(clienteSnit).registarPublicacao(PUBLICACAO, REFERENCIA);
        ordem.verify(manifestoService).comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, REFERENCIA);
        assertThat(mensagens(Level.INFO)).hasSize(3);
        assertThat(mensagens(Level.WARN)).isEmpty();
    }

    @Test
    void deveLevarOIdusDoProcessoNaReferenciaDasChamadas() {
        IdusProcesso idus = mock(IdusProcesso.class);
        when(idus.getIdus()).thenReturn("PDM1111-0001-01-031-01");
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(idus));
        ReferenciaChamada comIdus = new ReferenciaChamada(PROCESSO_ID, "PDM1111-0001-01-031-01", null);
        when(clienteSnit.registarPublicacao(PUBLICACAO, comIdus)).thenReturn(publicacao(201, null));
        when(manifestoService.comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, comIdus))
                .thenReturn(new ComunicacaoDepositoSnit(MANIFESTO_ID, deposito(201, null), ficheiros(201, null)));

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verify(clienteSnit).registarPublicacao(PUBLICACAO, comIdus);
        verify(manifestoService).comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, comIdus);
    }

    @Test
    void deveAceitarAPublicacaoJaRegistadaComDuzentos() {
        when(clienteSnit.registarPublicacao(PUBLICACAO, REFERENCIA)).thenReturn(publicacao(200, null));

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verify(manifestoService).comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, REFERENCIA);
    }

    static Stream<Arguments> falhasDaPublicacao() {
        return Stream.of(Arguments.of(409, FalhaTecnicaExterna.ERRO_HTTP),
                Arguments.of(503, FalhaTecnicaExterna.ERRO_HTTP),
                Arguments.of(null, FalhaTecnicaExterna.TIMEOUT),
                Arguments.of(null, FalhaTecnicaExterna.CIRCUITO_ABERTO),
                Arguments.of(null, FalhaTecnicaExterna.NAO_CONFIGURADO));
    }

    @ParameterizedTest
    @MethodSource("falhasDaPublicacao")
    void naoDeveTentarODepositoNemGravarOManifestoQuandoAPublicacaoFalha(Integer httpStatus,
                                                                          FalhaTecnicaExterna falha) {
        when(clienteSnit.registarPublicacao(PUBLICACAO, REFERENCIA)).thenReturn(publicacao(httpStatus, falha));

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verifyNoInteractions(manifestoService);
        verify(processo, never()).setEstado(any());
        assertThat(mensagens(Level.WARN)).singleElement().asString()
                .contains("sistema=SNIT", "passo=registarPublicacao", "processoId=" + PROCESSO_ID,
                        "numeroProcesso=00042", "httpStatus=" + httpStatus, "falha=" + falha);
    }

    @Test
    void deveAvisarSemFicheirosQuandoODepositoFalha() {
        when(manifestoService.comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, REFERENCIA))
                .thenReturn(new ComunicacaoDepositoSnit(MANIFESTO_ID, deposito(404, FalhaTecnicaExterna.ERRO_HTTP),
                        null));

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        assertThat(mensagens(Level.WARN)).singleElement().asString()
                .contains("passo=registarDeposito", "httpStatus=404");
    }

    @Test
    void deveAvisarQuandoOsFicheirosFalham() {
        when(manifestoService.comunicarDeposito(PROCESSO_ID, DEPOSITO, FICHEIROS, REFERENCIA))
                .thenReturn(new ComunicacaoDepositoSnit(MANIFESTO_ID, deposito(201, null),
                        ficheiros(500, FalhaTecnicaExterna.ERRO_HTTP)));

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        assertThat(mensagens(Level.WARN)).singleElement().asString()
                .contains("passo=registarFicheirosDeposito", "httpStatus=500");
    }

    @Test
    void deveRegistarSoAPublicacaoQuandoFaltaODeposito() {
        when(montador.montarDeposito(processo, AGORA)).thenReturn(Optional.empty());

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verify(clienteSnit).registarPublicacao(PUBLICACAO, REFERENCIA);
        verifyNoInteractions(manifestoService);
        verify(montador, never()).montarFicheiros(any());
    }

    @Test
    void naoDeveChamarOSnitQuandoFaltaUmDadoDaPublicacao() {
        when(montador.montarPublicacao(processo)).thenReturn(Optional.empty());

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verifyNoInteractions(clienteSnit, manifestoService);
    }

    @Test
    void naoDeveChamarOSnitParaUmProcedimentoQueNaoEOFinal() {
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(false);

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verifyNoInteractions(clienteSnit, manifestoService, montador);
    }

    @Test
    void naoDeveChamarOSnitParaUmProcessoDeRegistoExterno() {
        when(processo.getOrigemDados()).thenReturn("REGISTO_EXTERNO");

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verifyNoInteractions(clienteSnit, manifestoService, montador);
    }

    @Test
    void naoDeveChamarOSnitParaUmProcessoQueNaoEstaPublicado() {
        when(processo.getEstado()).thenReturn(EstadoProcesso.EM_VALIDACAO_INCM);

        service.registarProcedimentoFinal(PROCESSO_ID, REFERENCIA);

        verifyNoInteractions(clienteSnit, manifestoService, montador);
    }

    private List<String> mensagens(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static ResultadoChamadaExterna<PublicacaoRecebidaSnit> publicacao(Integer status, FalhaTecnicaExterna falha) {
        return new ResultadoChamadaExterna<>(status, null, falha, 5L);
    }

    private static ResultadoChamadaExterna<DepositoRecebidoSnit> deposito(Integer status, FalhaTecnicaExterna falha) {
        return new ResultadoChamadaExterna<>(status, null, falha, 5L);
    }

    private static ResultadoChamadaExterna<FicheirosRecebidosSnit> ficheiros(Integer status, FalhaTecnicaExterna falha) {
        return new ResultadoChamadaExterna<>(status, null, falha, 5L);
    }
}
