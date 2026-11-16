package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.CircuitoAbertoEvento;
import com.conkord.saigt.integracao.core.SistemaExterno;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AlertaIntegracaoListenerTest {

    private static final CircuitoAbertoEvento EVENTO =
            new CircuitoAbertoEvento(SistemaExterno.INCM, "incm-submissao", Instant.parse("2026-11-12T15:30:00Z"));

    private final AlertaIntegracaoService servico = mock(AlertaIntegracaoService.class);
    private final AlertaIntegracaoListener listener = new AlertaIntegracaoListener(servico);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertaIntegracaoListener.class);

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void deveCriarOAlertaDoCircuitoAberto() {
        listener.aoAbrirCircuito(EVENTO);

        verify(servico).criarAlertaCircuitoAberto(EVENTO);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void falhaAGravarNaoPropagaEDeixaUmErroComSistemaECircuito() {
        doThrow(new DataAccessResourceFailureException("BD em baixo")).when(servico).criarAlertaCircuitoAberto(EVENTO);

        assertThatCode(() -> listener.aoAbrirCircuito(EVENTO)).doesNotThrowAnyException();

        assertThat(logs.list).singleElement().satisfies(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.ERROR);
            assertThat(linha.getFormattedMessage()).contains("Falha a criar o alerta de integração",
                    "sistema=INCM", "circuito=incm-submissao");
            assertThat(linha.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void falhaNoCommitTambemNaoPropaga() {
        doThrow(new TransactionSystemException("commit falhou")).when(servico).criarAlertaCircuitoAberto(EVENTO);

        assertThatCode(() -> listener.aoAbrirCircuito(EVENTO)).doesNotThrowAnyException();

        assertThat(logs.list).singleElement().extracting(ILoggingEvent::getLevel).isEqualTo(Level.ERROR);
    }
}
