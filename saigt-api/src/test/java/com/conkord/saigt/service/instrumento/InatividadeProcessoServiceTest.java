package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InatividadeProcessoServiceTest {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
    private static final Instant AGORA = LocalDateTime.of(2026, 11, 11, 5, 30).atZone(LISBOA).toInstant();

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final IntencaoArmazenamentoService intencaoArmazenamentoService = mock(IntencaoArmazenamentoService.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final InatividadeProcessoService servico = new InatividadeProcessoService(processoRepository,
            processoDocumentoRepository, intencaoArmazenamentoService, auditoriaService, publicador,
            new InatividadeProcessoProperties(Period.ofMonths(3), Period.of(0, 3, 15), Period.ofMonths(4)));

    private final UUID id = UUID.randomUUID();

    private static Instant ha(Period periodo) {
        return AGORA.atZone(LISBOA).minus(periodo).toInstant();
    }

    private Processo processo(EstadoProcesso estado, Instant atualizadoEm, ProcessoDocumento... documentos) {
        Processo processo = new Processo();
        ReflectionTestUtils.setField(processo, "id", id);
        ReflectionTestUtils.setField(processo, "atualizadoEm", atualizadoEm);
        processo.setEstado(estado);
        when(processoRepository.findById(id)).thenReturn(Optional.of(processo));
        when(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(id)).thenReturn(List.of(documentos));
        return processo;
    }

    private static ProcessoDocumento documento(String caminho) {
        return new ProcessoDocumento(null, null, CategoriaDocumento.DEPOSITO, "pdf", "f.pdf", caminho, "c".repeat(64),
                "v1", 10L, null);
    }

    private Object eventoPublicado() {
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(publicador).publishEvent(evento.capture());
        return evento.getValue();
    }

    private void verificarQueNadaFoiGravado() {
        verify(processoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(intencaoArmazenamentoService, auditoriaService);
    }

    @Test
    void devePublicarOPrimeiroAvisoComADataDeAnulacaoPrevistaSemAlterarOProcesso() {
        Instant atualizadoEm = ha(Period.of(0, 3, 1));
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO, atualizadoEm);

        DesfechoInatividade desfecho = servico.avaliar(id, AGORA);

        assertThat(desfecho).isEqualTo(DesfechoInatividade.PRIMEIRO_AVISO);
        assertThat(eventoPublicado()).isEqualTo(new AvisoInatividadeProcessoEvento(id,
                LimiarInatividade.PRIMEIRO_AVISO, atualizadoEm.atZone(LISBOA).plusMonths(3).toInstant(),
                atualizadoEm, atualizadoEm.atZone(LISBOA).plusMonths(4).toLocalDate()));
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        verificarQueNadaFoiGravado();
    }

    @Test
    void devePublicarOPrimeiroAvisoQuandoPassaramExatamenteTresMeses() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(3)));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.PRIMEIRO_AVISO);
        assertThat(((AvisoInatividadeProcessoEvento) eventoPublicado()).momentoLimiar()).isEqualTo(AGORA);
    }

    @Test
    void naoDevePublicarNadaUmInstanteAntesDosTresMeses() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(3)).plusSeconds(1));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEM_ACAO);
        verifyNoInteractions(publicador);
        verificarQueNadaFoiGravado();
    }

    @Test
    void devePublicarSoOSegundoAvisoQuandoPassaramTresMesesEDezasseisDias() {
        Instant atualizadoEm = ha(Period.of(0, 3, 16));
        processo(EstadoProcesso.EM_SUBMISSAO, atualizadoEm);

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEGUNDO_AVISO);
        AvisoInatividadeProcessoEvento evento = (AvisoInatividadeProcessoEvento) eventoPublicado();
        assertThat(evento.limiar()).isEqualTo(LimiarInatividade.SEGUNDO_AVISO);
        assertThat(evento.momentoLimiar()).isEqualTo(atualizadoEm.atZone(LISBOA).plus(Period.of(0, 3, 15)).toInstant());
        verificarQueNadaFoiGravado();
    }

    @Test
    void devePublicarOSegundoAvisoQuandoPassaramExatamenteTresMesesEQuinzeDias() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 15)));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEGUNDO_AVISO);
    }

    @Test
    void deveAnularQuandoPassaramExatamenteQuatroMeses() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(4)));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.ANULADO);
    }

    @Test
    void deveAnularRegistarUmaIntencaoPorFicheiroAuditarEPublicarOEventoPorEstaOrdem() {
        Instant atualizadoEm = ha(Period.of(0, 4, 1));
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO, atualizadoEm,
                documento("processos/P1/a.pdf"), documento("processos/P1/b.pdf"), documento("processos/P1/c.pdf"));

        DesfechoInatividade desfecho = servico.avaliar(id, AGORA);

        assertThat(desfecho).isEqualTo(DesfechoInatividade.ANULADO);
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        InOrder ordem = inOrder(processoRepository, intencaoArmazenamentoService, auditoriaService, publicador);
        ordem.verify(processoRepository).saveAndFlush(processo);
        IntStream.of('a', 'b', 'c').forEach(letra -> ordem.verify(intencaoArmazenamentoService)
                .registar("processos/P1/" + (char) letra + ".pdf", OperacaoArmazenamento.APAGAMENTO));
        ordem.verify(auditoriaService).registarSistema("PROCESSO", id.toString(), "ANULACAO_INATIVIDADE",
                TransicaoEstado.de(EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ANULADO),
                Map.of("atualizadoEm", atualizadoEm, "ficheirosAApagar", 3));
        ordem.verify(publicador).publishEvent(new ProcessoAnuladoPorInatividadeEvento(id));
    }

    @Test
    void deveAnularSemAvisosQuandoOJobEsteveParadoEOProcessoJaPassouLargamenteOsQuatroMeses() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(7)));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.ANULADO);
        assertThat(eventoPublicado()).isEqualTo(new ProcessoAnuladoPorInatividadeEvento(id));
    }

    @Test
    void naoDeveRegistarIntencoesNemAuditoriaNemEventoQuandoAVersaoEntraEmConflito() {
        processo(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 4, 1)), documento("processos/P1/a.pdf"));
        when(processoRepository.saveAndFlush(any())).thenThrow(
                new ObjectOptimisticLockingFailureException(Processo.class, id));

        assertThatThrownBy(() -> servico.avaliar(id, AGORA)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verifyNoInteractions(intencaoArmazenamentoService, auditoriaService, publicador);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = "EM_SUBMISSAO", mode = EnumSource.Mode.EXCLUDE)
    void naoDeveFazerNadaQuandoOProcessoJaNaoEstaEmSubmissao(EstadoProcesso estado) {
        Processo processo = processo(estado, ha(Period.ofMonths(5)));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEM_ACAO);
        assertThat(processo.getEstado()).isEqualTo(estado);
        verifyNoInteractions(publicador);
        verificarQueNadaFoiGravado();
    }

    @Test
    void naoDeveFazerNadaQuandoOUtilizadorGravouOProcessoDepoisDaConsulta() {
        processo(EstadoProcesso.EM_SUBMISSAO, AGORA.minusSeconds(60));

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEM_ACAO);
        verifyNoInteractions(publicador);
        verificarQueNadaFoiGravado();
    }

    @Test
    void naoDeveFazerNadaQuandoOProcessoDeixouDeExistir() {
        when(processoRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(servico.avaliar(id, AGORA)).isEqualTo(DesfechoInatividade.SEM_ACAO);
        verifyNoInteractions(publicador);
    }

    @Test
    void deveIncluirNoLimiteDaConsultaOsFinsDeMesQueATrasDosTresMesesSaltaria() {
        Instant agora = LocalDateTime.of(2027, 2, 28, 5, 30).atZone(LISBOA).toInstant();

        Instant limite = servico.limiteDaConsulta(agora);

        assertThat(limite).isEqualTo(LocalDateTime.of(2026, 11, 30, 5, 30).atZone(LISBOA).toInstant());
    }

    @Test
    void deveUsarComoLimiteDaConsultaOInstanteDeHaTresMeses() {
        assertThat(servico.limiteDaConsulta(AGORA)).isEqualTo(ha(Period.ofMonths(3)));
        assertThat(LocalDate.ofInstant(servico.limiteDaConsulta(AGORA), LISBOA)).isEqualTo(LocalDate.of(2026, 8, 11));
    }
}
