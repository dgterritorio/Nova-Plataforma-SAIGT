package com.conkord.saigt.service.snit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SincronizacaoReferenciaSnitJobTest {

    private final SincronizacaoReferenciaSnitService service = mock(SincronizacaoReferenciaSnitService.class);
    private final SincronizacaoReferenciaSnitJob job = new SincronizacaoReferenciaSnitJob(service);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SincronizacaoReferenciaSnitJob.class);

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
    void deveSincronizarAsSeisListasPelaOrdem() {
        job.executar();

        InOrder ordem = inOrder(service);
        for (ListaReferenciaSnit lista : ListaReferenciaSnit.values()) {
            ordem.verify(service).sincronizar(lista);
        }
        ordem.verify(service).avisarDicoSemConcelho();
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
        assertThat(resumo()).isEqualTo(
                "Sincronização da réplica do SNIT concluída | job=sincronizacao-referencia-snit listas=6 falhas=0");
    }

    @Test
    void deveRegistarEmErrorAListaQueFalhaESeguirParaAsRestantes() {
        doThrow(new SnitIndisponivelException("Serviço do SNIT temporariamente indisponível."))
                .when(service).sincronizar(ListaReferenciaSnit.PROCEDURES);

        job.executar();

        InOrder ordem = inOrder(service);
        for (ListaReferenciaSnit lista : ListaReferenciaSnit.values()) {
            ordem.verify(service).sincronizar(lista);
        }
        ILoggingEvent erro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(erro.getFormattedMessage()).endsWith("lista=PROCEDURES");
        assertThat(erro.getThrowableProxy().getMessage()).isEqualTo("Serviço do SNIT temporariamente indisponível.");
        assertThat(resumo()).endsWith("listas=6 falhas=1");
    }

    @Test
    void naoDeveVerificarOsDicoQuandoOsConcelhosNaoSincronizam() {
        doThrow(new SnitIndisponivelException("Serviço do SNIT temporariamente indisponível."))
                .when(service).sincronizar(ListaReferenciaSnit.MUNICIPALITIES);

        job.executar();

        verify(service, never()).avisarDicoSemConcelho();
        assertThat(resumo()).endsWith("listas=6 falhas=1");
    }

    @Test
    void deveConcluirOJobMesmoQueAVerificacaoDosDicoFalhe() {
        doThrow(new IllegalStateException("BD indisponível")).when(service).avisarDicoSemConcelho();

        job.executar();

        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().startsWith("Falha na verificação dos DICO"));
        assertThat(resumo()).endsWith("listas=6 falhas=0");
    }

    private String resumo() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("job=sincronizacao-referencia-snit"))
                .findFirst().orElseThrow();
    }
}
