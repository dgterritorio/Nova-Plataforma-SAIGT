package com.conkord.saigt.service.incm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import com.conkord.saigt.service.snit.RegistoSnitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultaIncmJobTest {

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ConsultaIncmService consultaIncmService = mock(ConsultaIncmService.class);
    private final RegistoSnitService registoSnitService = mock(RegistoSnitService.class);
    private final ComunicacaoPcgtService comunicacaoPcgtService = mock(ComunicacaoPcgtService.class);
    private final ConsultaIncmJob job = new ConsultaIncmJob(processoRepository, consultaIncmService,
            registoSnitService, comunicacaoPcgtService);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ConsultaIncmJob.class);

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
    void deveConsultarOSeguinteERegistarEmErrorQuandoAConsultaDeUmProcessoLancaExcecao() {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(primeiro, segundo));
        IllegalStateException erro = new IllegalStateException("falha de teste");
        when(consultaIncmService.consultar(primeiro)).thenThrow(erro);
        when(consultaIncmService.consultar(segundo)).thenReturn(DesfechoConsultaIncm.PUBLICADO);

        job.executar();

        var ordem = inOrder(consultaIncmService);
        ordem.verify(consultaIncmService).consultar(primeiro);
        ordem.verify(consultaIncmService).consultar(segundo);
        ILoggingEvent linhaErro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(linhaErro.getFormattedMessage()).contains("processoId=" + primeiro);
        assertThat(linhaErro.getThrowableProxy().getMessage()).isEqualTo("falha de teste");
        assertThat(resumo()).endsWith("total=2 publicados=1 anulados=0 semAlteracao=0 falhas=1");
    }

    @Test
    void deveEscreverOResumoComOsTotaisPorDesfechoContandoAFalhaTecnicaComoFalha() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(ids);
        when(consultaIncmService.consultar(ids.get(0))).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(ids.get(1))).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(ids.get(2))).thenReturn(DesfechoConsultaIncm.ANULADO_INCM);
        when(consultaIncmService.consultar(ids.get(3))).thenReturn(DesfechoConsultaIncm.SEM_ALTERACAO);
        when(consultaIncmService.consultar(ids.get(4))).thenReturn(DesfechoConsultaIncm.FALHA_TECNICA);

        job.executar();

        assertThat(resumo()).isEqualTo(
                "Consulta à INCM concluída | job=consulta-incm total=5 publicados=2 anulados=1 semAlteracao=1 falhas=1");
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void deveEscreverOResumoAZeroQuandoNaoHaProcessosParaConsultar() {
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of());

        job.executar();

        assertThat(resumo()).endsWith("total=0 publicados=0 anulados=0 semAlteracao=0 falhas=0");
    }

    @Test
    void deveRegistarNoSnitSoOsProcessosQueAConsultaPublicou() {
        UUID publicado = UUID.randomUUID();
        UUID anulado = UUID.randomUUID();
        UUID semAlteracao = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(publicado, anulado, semAlteracao));
        when(consultaIncmService.consultar(publicado)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(anulado)).thenReturn(DesfechoConsultaIncm.ANULADO_INCM);
        when(consultaIncmService.consultar(semAlteracao)).thenReturn(DesfechoConsultaIncm.SEM_ALTERACAO);

        job.executar();

        var ordem = inOrder(consultaIncmService, registoSnitService);
        ordem.verify(consultaIncmService).consultar(publicado);
        ordem.verify(registoSnitService).registarProcedimentoFinal(publicado,
                new ReferenciaChamada(publicado, null, null));
        verify(registoSnitService, never()).registarProcedimentoFinal(eq(anulado), any());
        verify(registoSnitService, never()).registarProcedimentoFinal(eq(semAlteracao), any());
    }

    @Test
    void deveSeguirParaOProcessoSeguinteQuandoORegistoNoSnitLancaExcecao() {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(primeiro, segundo));
        when(consultaIncmService.consultar(primeiro)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(segundo)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        doThrow(new IllegalStateException("falha de teste")).when(registoSnitService)
                .registarProcedimentoFinal(eq(primeiro), any());

        job.executar();

        verify(registoSnitService).registarProcedimentoFinal(eq(segundo), any());
        ILoggingEvent linhaErro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(linhaErro.getFormattedMessage()).contains("SNIT").contains("processoId=" + primeiro);
        assertThat(resumo()).endsWith("total=2 publicados=2 anulados=0 semAlteracao=0 falhas=0");
    }

    @Test
    void deveComunicarAPcgtSoOsProcessosQueAConsultaPublicouDepoisDoRegistoNoSnit() {
        UUID publicado = UUID.randomUUID();
        UUID anulado = UUID.randomUUID();
        UUID semAlteracao = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(publicado, anulado, semAlteracao));
        when(consultaIncmService.consultar(publicado)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(anulado)).thenReturn(DesfechoConsultaIncm.ANULADO_INCM);
        when(consultaIncmService.consultar(semAlteracao)).thenReturn(DesfechoConsultaIncm.SEM_ALTERACAO);

        job.executar();

        var ordem = inOrder(consultaIncmService, registoSnitService, comunicacaoPcgtService);
        ordem.verify(consultaIncmService).consultar(publicado);
        ordem.verify(registoSnitService).registarProcedimentoFinal(eq(publicado), any());
        ordem.verify(comunicacaoPcgtService).comunicarPublicacao(publicado);
        verify(comunicacaoPcgtService, never()).comunicarPublicacao(anulado);
        verify(comunicacaoPcgtService, never()).comunicarPublicacao(semAlteracao);
    }

    @Test
    void deveComunicarAPcgtMesmoQueORegistoNoSnitLanceExcecao() {
        UUID processo = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(processo));
        when(consultaIncmService.consultar(processo)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        doThrow(new IllegalStateException("falha de teste")).when(registoSnitService)
                .registarProcedimentoFinal(eq(processo), any());

        job.executar();

        verify(comunicacaoPcgtService).comunicarPublicacao(processo);
    }

    @Test
    void deveSeguirParaOProcessoSeguinteQuandoAComunicacaoAPcgtLancaExcecao() {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(processoRepository.findIdsParaConsultaIncm()).thenReturn(List.of(primeiro, segundo));
        when(consultaIncmService.consultar(primeiro)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        when(consultaIncmService.consultar(segundo)).thenReturn(DesfechoConsultaIncm.PUBLICADO);
        doThrow(new IllegalStateException("falha de teste")).when(comunicacaoPcgtService).comunicarPublicacao(primeiro);

        job.executar();

        verify(consultaIncmService).consultar(segundo);
        verify(comunicacaoPcgtService).comunicarPublicacao(segundo);
        ILoggingEvent linhaErro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(linhaErro.getFormattedMessage()).contains("PCGT").contains("continua publicado")
                .contains("processoId=" + primeiro);
        assertThat(resumo()).endsWith("total=2 publicados=2 anulados=0 semAlteracao=0 falhas=0");
    }

    private String resumo() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("job=consulta-incm"))
                .findFirst().orElseThrow();
    }
}
