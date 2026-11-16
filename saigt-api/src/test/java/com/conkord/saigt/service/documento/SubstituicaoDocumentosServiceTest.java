package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.DocumentoVersaoDesatualizadaException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubstituicaoDocumentosServiceTest {

    private static final String NUMERO_PROCESSO = "00042";
    private static final String ORIGINAL = "processos/" + NUMERO_PROCESSO + "/PUBLICACAO/doc-regulamento.pdf";

    private final ProcessoDocumentoRepository repositorio = mock(ProcessoDocumentoRepository.class);
    private final ArmazenamentoDocumentos armazenamento = mock(ArmazenamentoDocumentos.class);
    private final IntencaoArmazenamentoService intencoes = mock(IntencaoArmazenamentoService.class);
    private final LinkPublicoService links = mock(LinkPublicoService.class);
    private final SubstituicaoDocumentosService servico =
            new SubstituicaoDocumentosService(repositorio, armazenamento, intencoes, links);

    private ProcessoDocumento documento;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        Processo processo = new Processo();
        ReflectionTestUtils.setField(processo, "numeroProcesso", NUMERO_PROCESSO);
        documento = new ProcessoDocumento();
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(documento, "processo", processo);
        ReflectionTestUtils.setField(documento, "nomeFicheiro", "regulamento.pdf");
        documento.setCaminhoFicheiro(ORIGINAL);
        documento.setVersaoObjeto("v-original");
        when(armazenamento.copiar(eq(ORIGINAL), eq("v-original"), anyString())).thenReturn("v-copia");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deveCopiarPelaVersaoParaSubstituidosApontarODocumentoParaACopiaERevogarOsLinks() {
        servico.moverParaSubstituidos(documento);

        assertThat(documento.getCaminhoFicheiro()).matches("processos/" + NUMERO_PROCESSO + "/SUBSTITUIDOS/"
                + documento.getId() + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(documento.getVersaoObjeto()).isEqualTo("v-copia");
        assertThat(documento.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        InOrder ordem = inOrder(intencoes, armazenamento, links, repositorio);
        ordem.verify(intencoes).registar(documento.getCaminhoFicheiro(), OperacaoArmazenamento.COPIA);
        ordem.verify(armazenamento).copiar(ORIGINAL, "v-original", documento.getCaminhoFicheiro());
        ordem.verify(links).revogar(documento.getId(), MotivoRevogacaoLink.SUBSTITUICAO);
        ordem.verify(repositorio).flush();
        ordem.verify(intencoes).registar(ORIGINAL, OperacaoArmazenamento.APAGAMENTO);
        verify(armazenamento, never()).removerTodasVersoes(any());
        verify(armazenamento, never()).reter(any(), any(), any());
    }

    @Test
    void deveReterACopiaAteADataDoOriginalDepoisDoFlushQuandoOOriginalEstaRetido() {
        Instant ate = Instant.parse("2126-11-11T12:00:00Z");
        when(armazenamento.retidoAte(ORIGINAL, "v-original")).thenReturn(Optional.of(ate));

        servico.moverParaSubstituidos(documento);

        InOrder ordem = inOrder(armazenamento, repositorio);
        ordem.verify(armazenamento).copiar(ORIGINAL, "v-original", documento.getCaminhoFicheiro());
        ordem.verify(repositorio).flush();
        ordem.verify(armazenamento).reter(documento.getCaminhoFicheiro(), "v-copia", ate);
    }

    @Test
    void deveFalharEApagarACopiaNoRollbackQuandoARetencaoDaCopiaFalha() {
        when(armazenamento.retidoAte(ORIGINAL, "v-original")).thenReturn(Optional.of(Instant.parse("2126-11-11T12:00:00Z")));
        doThrow(new StorageIndisponivelException("indisponível", null))
                .when(armazenamento).reter(anyString(), eq("v-copia"), any());

        assertThatThrownBy(() -> servico.moverParaSubstituidos(documento))
                .isInstanceOf(StorageIndisponivelException.class);
        String copia = documento.getCaminhoFicheiro();
        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(armazenamento).removerTodasVersoes(copia);
        verify(intencoes, never()).registar(ORIGINAL, OperacaoArmazenamento.APAGAMENTO);
    }

    @Test
    void deveManterAPastaGeoconformNaCopiaDeUmOutputDoGeoconform() {
        documento.setOrigem(OrigemDocumento.GEOCONFORM);
        String output = "processos/" + NUMERO_PROCESSO + "/DEPOSITO/GEOCONFORM/doc-regulamento.pdf";
        documento.setCaminhoFicheiro(output);
        when(armazenamento.copiar(eq(output), eq("v-original"), anyString())).thenReturn("v-copia");

        servico.moverParaSubstituidos(documento);

        assertThat(documento.getCaminhoFicheiro()).matches("processos/" + NUMERO_PROCESSO + "/SUBSTITUIDOS/GEOCONFORM/"
                + documento.getId() + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(documento.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
    }

    @Test
    void deveDeixarODocumentoComoEstavaQuandoACopiaFalha() {
        doThrow(new StorageIndisponivelException("indisponível", null))
                .when(armazenamento).copiar(eq(ORIGINAL), eq("v-original"), anyString());

        assertThatThrownBy(() -> servico.moverParaSubstituidos(documento))
                .isInstanceOf(StorageIndisponivelException.class);

        assertThat(documento.getCaminhoFicheiro()).isEqualTo(ORIGINAL);
        assertThat(documento.getVersaoObjeto()).isEqualTo("v-original");
        assertThat(documento.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        verify(links, never()).revogar(any(), any());
        verify(intencoes, never()).registar(ORIGINAL, OperacaoArmazenamento.APAGAMENTO);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void deveRecusarComVersaoDesatualizadaQuandoOutroPedidoMudouODocumento() {
        doThrow(new ObjectOptimisticLockingFailureException(ProcessoDocumento.class, documento.getId()))
                .when(repositorio).flush();

        assertThatThrownBy(() -> servico.moverParaSubstituidos(documento))
                .isInstanceOf(DocumentoVersaoDesatualizadaException.class);
    }

    @Test
    void deveApagarTodasAsVersoesDoOriginalDepoisDoCommitQuandoNenhumaEstaRetida() {
        when(armazenamento.versoes(ORIGINAL)).thenReturn(List.of(new VersaoObjeto("v-original", false)));

        servico.moverParaSubstituidos(documento);
        sincronizacoes().forEach(TransactionSynchronization::afterCommit);

        verify(armazenamento).removerTodasVersoes(ORIGINAL);
        verify(armazenamento, never()).marcarEliminado(any());
    }

    @Test
    void deveCriarSoUmMarcadorDeEliminacaoQuandoOOriginalTemUmaVersaoRetida() {
        when(armazenamento.versoes(ORIGINAL)).thenReturn(List.of(new VersaoObjeto("v-original", true)));

        servico.moverParaSubstituidos(documento);
        sincronizacoes().forEach(TransactionSynchronization::afterCommit);

        verify(armazenamento).marcarEliminado(ORIGINAL);
        verify(armazenamento, never()).removerTodasVersoes(ORIGINAL);
    }

    @Test
    void deveDeixarOOriginalParaALimpezaNoturnaQuandoOApagamentoFalhaDepoisDoCommit() {
        when(armazenamento.versoes(ORIGINAL)).thenReturn(List.of());
        doThrow(new StorageIndisponivelException("indisponível", null)).when(armazenamento).removerTodasVersoes(ORIGINAL);

        servico.moverParaSubstituidos(documento);
        sincronizacoes().forEach(TransactionSynchronization::afterCommit);

        verify(intencoes).registar(ORIGINAL, OperacaoArmazenamento.APAGAMENTO);
    }

    @Test
    void deveApagarACopiaQuandoATransacaoReverteENuncaQuandoFazCommit() {
        servico.moverParaSubstituidos(documento);
        String copia = documento.getCaminhoFicheiro();

        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(armazenamento, never()).removerTodasVersoes(copia);

        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(armazenamento).removerTodasVersoes(copia);
        verify(armazenamento, never()).removerTodasVersoes(ORIGINAL);
    }

    private static List<TransactionSynchronization> sincronizacoes() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}
