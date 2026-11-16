package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.AlertaCaducidadeDinamicaEvento;
import com.conkord.saigt.service.instrumento.AvisoInatividadeProcessoEvento;
import com.conkord.saigt.service.instrumento.LimiarCaducidade;
import com.conkord.saigt.service.instrumento.LimiarInatividade;
import com.conkord.saigt.service.instrumento.ProcessoAnuladoPorInatividadeEvento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertasJobsListenerTest {

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final AlertaProcessoService alertaProcessoService = mock(AlertaProcessoService.class);
    private final AlertasJobsListener listener =
            new AlertasJobsListener(processoRepository, notificacaoRepository, alertaProcessoService);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertasJobsListener.class);

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private ILoggingEvent erro() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
    }

    @Test
    void falhaNoAvisoDeInatividadeFicaNoLogComOProcessoIdESemExcecaoParaOJob() {
        UUID processoId = UUID.randomUUID();
        when(processoRepository.findById(processoId)).thenThrow(new DataAccessResourceFailureException("BD em baixo"));

        assertThatCode(() -> listener.aoAvisoInatividade(new AvisoInatividadeProcessoEvento(processoId,
                LimiarInatividade.PRIMEIRO_AVISO, Instant.now(), Instant.now(), LocalDate.now())))
                .doesNotThrowAnyException();

        assertThat(erro().getFormattedMessage()).contains("processoId=" + processoId);
        verifyNoInteractions(alertaProcessoService);
    }

    @Test
    void falhaNaAnulacaoFicaNoLogComOProcessoIdEOEstado() {
        UUID processoId = UUID.randomUUID();
        when(processoRepository.findById(processoId)).thenThrow(new DataAccessResourceFailureException("BD em baixo"));

        assertThatCode(() -> listener.aoAnuladoPorInatividade(new ProcessoAnuladoPorInatividadeEvento(processoId)))
                .doesNotThrowAnyException();

        assertThat(erro().getFormattedMessage()).contains("processoId=" + processoId).contains("estado=ANULADO");
    }

    @Test
    void falhaNoAlertaDeCaducidadeFicaNoLogComODinamicaId() {
        UUID dinamicaId = UUID.randomUUID();
        when(processoRepository
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByDataPublicacaoDrDesc(
                        any(), anyString(), any(EstadoProcesso.class)))
                .thenThrow(new DataAccessResourceFailureException("BD em baixo"));

        assertThatCode(() -> listener.aoAlertaCaducidade(
                new AlertaCaducidadeDinamicaEvento(dinamicaId, LimiarCaducidade.DATA, LocalDate.now())))
                .doesNotThrowAnyException();

        assertThat(erro().getFormattedMessage()).contains("dinamicaId=" + dinamicaId);
    }

    @Test
    void identificacaoDaDinamicaSoLevaODescritivoQuandoExiste() {
        assertThat(AlertasJobsListener.identificacao(dinamica("00012", "Revisão do PDM"))).isEqualTo("00012 — Revisão do PDM");
        assertThat(AlertasJobsListener.identificacao(dinamica("00012", null))).isEqualTo("00012");
        assertThat(AlertasJobsListener.identificacao(dinamica("00012", "  "))).isEqualTo("00012");
    }

    private static Dinamica dinamica(String numero, String descritivo) {
        Dinamica dinamica = new Dinamica();
        ReflectionTestUtils.setField(dinamica, "numeroDinamica", numero);
        ReflectionTestUtils.setField(dinamica, "descritivo", descritivo);
        return dinamica;
    }
}
