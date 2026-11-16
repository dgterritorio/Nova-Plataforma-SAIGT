package com.conkord.saigt.service.instrumento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InatividadeProcessoJobTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T05:30:00Z");
    private static final Instant LIMITE = Instant.parse("2026-08-11T04:30:00Z");

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final InatividadeProcessoService servico = mock(InatividadeProcessoService.class);
    private final InatividadeProcessoJob job =
            new InatividadeProcessoJob(processoRepository, servico, Clock.fixed(AGORA, ZoneOffset.UTC));

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(InatividadeProcessoJob.class);

    private final UUID primeiro = UUID.randomUUID();
    private final UUID segundo = UUID.randomUUID();
    private final UUID terceiro = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        when(servico.limiteDaConsulta(AGORA)).thenReturn(LIMITE);
        when(processoRepository.findIdsEmSubmissaoSemAtualizacaoDesde(LIMITE))
                .thenReturn(List.of(primeiro, segundo, terceiro));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void deveContarCadaDesfechoNoLogFinalSoComIdsEContagens() {
        when(servico.avaliar(primeiro, AGORA)).thenReturn(DesfechoInatividade.PRIMEIRO_AVISO);
        when(servico.avaliar(segundo, AGORA)).thenReturn(DesfechoInatividade.SEGUNDO_AVISO);
        when(servico.avaliar(terceiro, AGORA)).thenReturn(DesfechoInatividade.ANULADO);

        job.executar();

        assertThat(resumo().getLevel()).isEqualTo(Level.INFO);
        assertThat(resumo().getFormattedMessage()).isEqualTo("Verificação de inatividade concluída "
                + "| job=inatividade-processos total=3 primeirosAvisos=1 segundosAvisos=1 anulados=1 falhas=0");
    }

    @Test
    void deveTratarOPrimeiroEOTerceiroQuandoOSegundoLancaExcecaoEContarUmaFalha() {
        when(servico.avaliar(primeiro, AGORA)).thenReturn(DesfechoInatividade.ANULADO);
        when(servico.avaliar(segundo, AGORA)).thenThrow(new IllegalStateException("falha de teste"));
        when(servico.avaliar(terceiro, AGORA)).thenReturn(DesfechoInatividade.ANULADO);

        job.executar();

        verify(servico).avaliar(terceiro, AGORA);
        ILoggingEvent erro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(erro.getFormattedMessage()).contains("processoId=" + segundo);
        assertThat(resumo().getFormattedMessage()).contains("anulados=2 falhas=1");
    }

    @Test
    void deveRegistarEmWarnESeguirSemContarFalhaQuandoAVersaoEntraEmConflito() {
        when(servico.avaliar(primeiro, AGORA)).thenReturn(DesfechoInatividade.SEM_ACAO);
        when(servico.avaliar(segundo, AGORA)).thenThrow(new ObjectOptimisticLockingFailureException(Processo.class, segundo));
        when(servico.avaliar(terceiro, AGORA)).thenReturn(DesfechoInatividade.ANULADO);

        job.executar();

        verify(servico).avaliar(terceiro, AGORA);
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("processoId=" + segundo));
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
        assertThat(resumo().getFormattedMessage()).contains("anulados=1 falhas=0");
    }

    private ILoggingEvent resumo() {
        return logs.list.stream().filter(e -> e.getFormattedMessage().contains("job=inatividade-processos"))
                .findFirst().orElseThrow();
    }
}
