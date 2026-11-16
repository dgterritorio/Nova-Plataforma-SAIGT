package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.documento.LimpezaArmazenamentoService.ResultadoLimpeza;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimpezaArmazenamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T03:30:00Z");
    private static final String CHAVE = "processos/00123/PUBLICACAO/0b9e6d2c-5f1a-4c3e-9a7b-2d8f1e4c6a90-regulamento.pdf";

    private final ArmazenamentoIntencaoRepository intencoes = mock(ArmazenamentoIntencaoRepository.class);
    private final ProcessoDocumentoRepository documentos = mock(ProcessoDocumentoRepository.class);
    private final NotificacaoDocumentoRepository anexos = mock(NotificacaoDocumentoRepository.class);
    private final ArmazenamentoDocumentos armazenamento = mock(ArmazenamentoDocumentos.class);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final LimpezaArmazenamentoService servico = new LimpezaArmazenamentoService(intencoes, documentos,
            anexos, armazenamento, metricas, Duration.ofHours(24));

    @Test
    void deveProcurarSoAsIntencoesComMaisDe24h() {
        when(intencoes.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), anyLong())).thenReturn(List.of());

        servico.limpar(AGORA);

        verify(intencoes).findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(AGORA.minus(Duration.ofHours(24)), 0L);
        verify(armazenamento, never()).remover(any());
    }

    @Test
    void deveApagarOObjetoEDescartarAIntencaoDeGravacaoSemLinha() {
        ArmazenamentoIntencao intencao = intencao(1L, OperacaoArmazenamento.GRAVACAO);
        devolver(intencao);
        when(documentos.findCaminhosExistentes(anyCollection())).thenReturn(List.of());
        when(armazenamento.versoes(CHAVE)).thenReturn(List.of(new VersaoObjeto("v1", false)));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(armazenamento).remover(CHAVE);
        verify(intencoes).deleteById(1L);
        assertThat(resultado).containsEntry(ResultadoLimpeza.APAGADA, 1);
        assertThat(metricas.counter("documento.limpeza.total", "resultado", "apagada").count()).isEqualTo(1);
    }

    @Test
    void deveDescartarAIntencaoDeGravacaoComLinhaSemTocarNoObjeto() {
        ArmazenamentoIntencao intencao = intencao(2L, OperacaoArmazenamento.GRAVACAO);
        devolver(intencao);
        when(documentos.findCaminhosExistentes(anyCollection())).thenReturn(List.of(CHAVE));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(armazenamento, never()).versoes(any());
        verify(armazenamento, never()).remover(any());
        verify(intencoes).deleteById(2L);
        assertThat(resultado).containsEntry(ResultadoLimpeza.DESCARTADA, 1);
    }

    @Test
    void deveDescartarAIntencaoDeGravacaoDeAnexoDeNotificacaoComLinha() {
        String chaveAnexo = "notificacoes/7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d-oficio.pdf";
        ArmazenamentoIntencao intencao = new ArmazenamentoIntencao(chaveAnexo, OperacaoArmazenamento.GRAVACAO);
        ReflectionTestUtils.setField(intencao, "id", 9L);
        devolver(intencao);
        when(documentos.findCaminhosExistentes(anyCollection())).thenReturn(List.of());
        when(anexos.findCaminhosExistentes(anyCollection())).thenReturn(List.of(chaveAnexo));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(armazenamento, never()).remover(any());
        verify(intencoes).deleteById(9L);
        assertThat(resultado).containsEntry(ResultadoLimpeza.DESCARTADA, 1);
    }

    @Test
    void deveDescartarAIntencaoDeCopiaComLinha() {
        devolver(intencao(3L, OperacaoArmazenamento.COPIA));
        when(documentos.findCaminhosExistentes(anyCollection())).thenReturn(List.of(CHAVE));

        servico.limpar(AGORA);

        verify(armazenamento, never()).remover(any());
        verify(intencoes).deleteById(3L);
    }

    @Test
    void deveApagarMesmoComLinhaQuandoAIntencaoEDeApagamento() {
        devolver(intencao(4L, OperacaoArmazenamento.APAGAMENTO));
        when(armazenamento.versoes(CHAVE)).thenReturn(List.of(new VersaoObjeto("v1", false),
                new VersaoObjeto("v2", false)));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(documentos, never()).findCaminhosExistentes(anyCollection());
        verify(armazenamento).remover(CHAVE);
        verify(intencoes).deleteById(4L);
        assertThat(resultado).containsEntry(ResultadoLimpeza.APAGADA, 1);
    }

    @Test
    void naoDeveApagarQuandoAlgumaVersaoEstaRetidaEDeveDescartarAIntencao() {
        devolver(intencao(5L, OperacaoArmazenamento.APAGAMENTO));
        when(armazenamento.versoes(CHAVE)).thenReturn(List.of(new VersaoObjeto("v1", true),
                new VersaoObjeto("marcador", false)));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(armazenamento, never()).remover(any());
        verify(intencoes).deleteById(5L);
        assertThat(resultado).containsEntry(ResultadoLimpeza.RETIDA, 1);
    }

    @Test
    void deveManterAIntencaoParaAExecucaoSeguinteComOStorageIndisponivel() {
        devolver(intencao(6L, OperacaoArmazenamento.GRAVACAO));
        when(documentos.findCaminhosExistentes(anyCollection())).thenReturn(List.of());
        when(armazenamento.versoes(CHAVE)).thenThrow(new StorageIndisponivelException("indisponível", null));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(AGORA);

        verify(intencoes, never()).deleteById(any());
        assertThat(resultado).containsEntry(ResultadoLimpeza.FALHADA, 1);
    }

    @Test
    void devePedirOLoteSeguinteDepoisDoUltimoIdQuandoUmaIntencaoFica() {
        ArmazenamentoIntencao falhada = intencao(7L, OperacaoArmazenamento.APAGAMENTO);
        when(intencoes.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), eq(0L)))
                .thenReturn(List.of(falhada));
        when(intencoes.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), eq(7L)))
                .thenReturn(List.of());
        when(armazenamento.versoes(CHAVE)).thenThrow(new StorageIndisponivelException("indisponível", null));

        servico.limpar(AGORA);

        verify(intencoes).findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), eq(7L));
    }

    private void devolver(ArmazenamentoIntencao intencao) {
        when(intencoes.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), eq(0L)))
                .thenReturn(List.of(intencao));
        when(intencoes.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(any(), eq(intencao.getId())))
                .thenReturn(List.of());
    }

    private static ArmazenamentoIntencao intencao(Long id, OperacaoArmazenamento operacao) {
        ArmazenamentoIntencao intencao = new ArmazenamentoIntencao(CHAVE, operacao);
        ReflectionTestUtils.setField(intencao, "id", id);
        return intencao;
    }
}
