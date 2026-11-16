package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.DocumentoVersaoDesatualizadaException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O conflito de `versao` do documento sai como 409 e não deixa objeto novo no storage. */
class ProcessoDocumentoServiceConflitoVersaoTest {

    private static final String CAMINHO_ATUAL = "processos/00001/DEPOSITO/atual-antigo.pdf";

    private final ProcessoDocumentoRepository repositorio = mock(ProcessoDocumentoRepository.class);
    private final ArmazenamentoDocumentos armazenamento = mock(ArmazenamentoDocumentos.class);
    private final UUID processoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private ProcessoDocumentoService servico;

    @BeforeEach
    void preparar() {
        Processo processo = mock(Processo.class);
        when(processo.getNumeroProcesso()).thenReturn("00001");
        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        when(procedimento.getTamanhoMaximoProcesso()).thenReturn(null);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        ProcessoAlteravel processoAlteravel = mock(ProcessoAlteravel.class);
        ContextoUtilizadorResolver resolver = mock(ContextoUtilizadorResolver.class);
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, null, null, null, null,
                null, null, null, null);
        when(resolver.resolver()).thenReturn(contexto);
        when(processoAlteravel.obter(processoId, contexto)).thenReturn(processo);
        when(processoAlteravel.obterComBloqueio(processoId, contexto)).thenReturn(processo);
        ProcessoDocumento documento = new ProcessoDocumento(documentoId, processo, null, CategoriaDocumento.DEPOSITO,
                "pdf", "antigo.pdf", CAMINHO_ATUAL, "a".repeat(64), "v1", 10L, null);
        when(repositorio.findByIdAndProcesso_Id(documentoId, processoId)).thenReturn(Optional.of(documento));
        doThrow(new ObjectOptimisticLockingFailureException(ProcessoDocumento.class, documentoId))
                .when(repositorio).flush();

        servico = new ProcessoDocumentoService(repositorio, processoAlteravel, mock(PlantaService.class),
                mock(MetadadosMotuService.class), mock(UtilizadorRepository.class),
                resolver, mock(AuditoriaService.class), armazenamento, mock(IntencaoArmazenamentoService.class),
                mock(LinkPublicoService.class), mock(VerificadorZip.class), mock(VerificadorTiff.class),
                mock(SubstituicaoDocumentosService.class), mock(ValidadorNomeSubstituicao.class),
                new GestorSemRecursos());
    }

    @Test
    void deveDevolverConflitoEApagarOObjetoNovoQuandoOutraEscritaAlteraODocumentoNaSubstituicao() {
        doAnswer(invocacao -> {
            invocacao.<InputStream>getArgument(1).readAllBytes();
            return "v2";
        }).when(armazenamento).guardar(anyString(), any(InputStream.class), anyString());
        PedidoUpload pedido = new PedidoUpload("DEPOSITO", null, List.of(), "novo.pdf", "application/pdf",
                new ByteArrayInputStream("conteudo".getBytes()), () -> { });

        assertThatThrownBy(() -> servico.substituir(processoId, documentoId, pedido))
                .isInstanceOf(DocumentoVersaoDesatualizadaException.class);

        verify(armazenamento, atLeastOnce()).removerTodasVersoes(startsWith("processos/00001/DEPOSITO/" + documentoId + "-"));
        verify(armazenamento, never()).removerTodasVersoes(eq(CAMINHO_ATUAL));
    }

    @Test
    void deveDevolverConflitoSemApagarOObjetoQuandoOutraEscritaAlteraODocumentoNaRemocao() {
        assertThatThrownBy(() -> servico.remover(processoId, documentoId))
                .isInstanceOf(DocumentoVersaoDesatualizadaException.class);

        verify(armazenamento, never()).removerTodasVersoes(anyString());
    }

    /** Só o necessário para haver sincronização de transação, que o serviço usa para limpar o storage. */
    private static final class GestorSemRecursos extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // Sem recursos para abrir.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // Sem recursos para confirmar.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // Sem recursos para reverter.
        }
    }
}
