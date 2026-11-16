package com.conkord.saigt.service.instrumento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaducidadeDinamicaJobTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final CaducidadeDinamicaService servico = mock(CaducidadeDinamicaService.class);
    private final CaducidadeDinamicaJob job = new CaducidadeDinamicaJob(dinamicaRepository, servico);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CaducidadeDinamicaJob.class);

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
    void deveAvaliarAsDinamicasDeHojeEmLisboaComUmaMargemSobreOLimiteDeUmMes() {
        LocalDate hoje = LocalDate.now(ZoneId.of("Europe/Lisbon"));
        when(dinamicaRepository.findIdsParaVerificacaoCaducidade(any())).thenReturn(List.of());

        job.executar();

        verify(dinamicaRepository).findIdsParaVerificacaoCaducidade(hoje.plusMonths(1).plusDays(3));
    }

    @Test
    void deveAvaliarASeguinteERegistarEmErrorQuandoUmaDinamicaLancaExcecao() {
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        UUID terceira = UUID.randomUUID();
        when(dinamicaRepository.findIdsParaVerificacaoCaducidade(any())).thenReturn(List.of(primeira, segunda, terceira));
        when(servico.avaliar(eq(primeira), any())).thenReturn(Optional.of(LimiarCaducidade.UM_MES));
        when(servico.avaliar(eq(segunda), any())).thenThrow(new IllegalStateException("falha de teste"));
        when(servico.avaliar(eq(terceira), any())).thenReturn(Optional.of(LimiarCaducidade.DATA));

        job.executar();

        var ordem = inOrder(servico);
        ordem.verify(servico).avaliar(eq(primeira), any());
        ordem.verify(servico).avaliar(eq(segunda), any());
        ordem.verify(servico).avaliar(eq(terceira), any());
        ILoggingEvent linhaErro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(linhaErro.getFormattedMessage()).contains("dinamicaId=" + segunda);
        assertThat(linhaErro.getThrowableProxy().getMessage()).isEqualTo("falha de teste");
        assertThat(resumo()).endsWith("total=3 umMes=1 quinzeDias=0 caducadas=1 falhas=1");
    }

    @Test
    void deveEscreverOResumoComAsContagensPorLimiar() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(dinamicaRepository.findIdsParaVerificacaoCaducidade(any())).thenReturn(ids);
        when(servico.avaliar(eq(ids.get(0)), any())).thenReturn(Optional.of(LimiarCaducidade.UM_MES));
        when(servico.avaliar(eq(ids.get(1)), any())).thenReturn(Optional.of(LimiarCaducidade.QUINZE_DIAS));
        when(servico.avaliar(eq(ids.get(2)), any())).thenReturn(Optional.of(LimiarCaducidade.QUINZE_DIAS));
        when(servico.avaliar(eq(ids.get(3)), any())).thenReturn(Optional.of(LimiarCaducidade.DATA));
        when(servico.avaliar(eq(ids.get(4)), any())).thenReturn(Optional.empty());

        job.executar();

        assertThat(resumo()).isEqualTo("Verificação de caducidade concluída | job=caducidade-dinamicas total=5 umMes=1 "
                + "quinzeDias=2 caducadas=1 falhas=0");
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void deveEscreverOResumoAZeroQuandoNaoHaDinamicasParaVerificar() {
        when(dinamicaRepository.findIdsParaVerificacaoCaducidade(any())).thenReturn(List.of());

        job.executar();

        assertThat(resumo()).endsWith("total=0 umMes=0 quinzeDias=0 caducadas=0 falhas=0");
    }

    private String resumo() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("job=caducidade-dinamicas"))
                .findFirst().orElseThrow();
    }
}
