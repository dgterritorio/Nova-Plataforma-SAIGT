package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetencaoDocumentosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant ATE = Instant.parse("2126-11-11T10:00:00Z");
    private static final String NUMERO = "2026-000123";

    private final ProcessoDocumentoRepository repository = mock(ProcessoDocumentoRepository.class);
    private final ArmazenamentoDocumentos armazenamento = mock(ArmazenamentoDocumentos.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final RetencaoDocumentosService service = new RetencaoDocumentosService(repository, armazenamento,
            auditoriaService, Clock.fixed(AGORA, ZoneOffset.UTC), 100);

    private final Processo processo = mock(Processo.class);
    private final UUID processoId = UUID.randomUUID();

    @Test
    void deveReterSoOsDocumentosAtuaisDoProcessoAindaSemRetencaoEAuditar() {
        ProcessoDocumento semRetencao = documento("processos/" + NUMERO + "/PUBLICACAO/a-regulamento.pdf", "v1");
        ProcessoDocumento jaRetido = documento("processos/" + NUMERO + "/DEPOSITO/b-planta.pdf", "v2");
        ProcessoDocumento substituido = documento("processos/" + NUMERO + "/SUBSTITUIDOS/c-1-antigo.pdf", "v3");
        ProcessoDocumento foraDoProcesso = documento("notificacoes/" + NUMERO + "/anexo.pdf", "v4");
        dadoOsDocumentos(semRetencao, jaRetido, substituido, foraDoProcesso);
        when(armazenamento.temRetencao(jaRetido.getCaminhoFicheiro(), "v2")).thenReturn(true);

        int retidos = service.reterDocumentosSemRetencao(processo, MomentoRetencao.ENTRADA_VALIDADO_DGT);

        assertThat(retidos).isEqualTo(1);
        verify(armazenamento).reter(semRetencao.getCaminhoFicheiro(), "v1", ATE);
        verify(armazenamento, never()).reter(jaRetido.getCaminhoFicheiro(), "v2", ATE);
        verify(armazenamento, never()).temRetencao(substituido.getCaminhoFicheiro(), "v3");
        verify(armazenamento, never()).temRetencao(foraDoProcesso.getCaminhoFicheiro(), "v4");
        verify(auditoriaService).registarSistema("PROCESSO", processoId.toString(), "RETER_DOCUMENTOS",
                Map.of("momento", "ENTRADA_VALIDADO_DGT", "ficheirosRetidos", 1, "retidoAte", ATE.toString()));
    }

    @Test
    void naoDeveChamarReterEDeveAuditarZeroFicheirosQuandoNaoHaDocumentosSemRetencao() {
        ProcessoDocumento jaRetido = documento("processos/" + NUMERO + "/PUBLICACAO/a-regulamento.pdf", "v1");
        dadoOsDocumentos(jaRetido);
        when(armazenamento.temRetencao(anyString(), anyString())).thenReturn(true);

        int retidos = service.reterDocumentosSemRetencao(processo, MomentoRetencao.SAIDA_VALIDADO_DGT);

        assertThat(retidos).isZero();
        verify(armazenamento, never()).reter(anyString(), any(), any());
        verify(auditoriaService).registarSistema("PROCESSO", processoId.toString(), "RETER_DOCUMENTOS",
                Map.of("momento", "SAIDA_VALIDADO_DGT", "ficheirosRetidos", 0, "retidoAte", ATE.toString()));
    }

    private void dadoOsDocumentos(ProcessoDocumento... documentos) {
        when(processo.getId()).thenReturn(processoId);
        when(processo.getNumeroProcesso()).thenReturn(NUMERO);
        when(repository.findByProcesso_IdOrderByCriadoEmAsc(processoId)).thenReturn(List.of(documentos));
    }

    private static ProcessoDocumento documento(String caminho, String versao) {
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        when(documento.getCaminhoFicheiro()).thenReturn(caminho);
        when(documento.getVersaoObjeto()).thenReturn(versao);
        return documento;
    }
}
