package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.DocumentoEstadoInvalidoException;
import com.conkord.saigt.exception.JustificacaoEmFaltaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** «Desbloquear ficheiro»: estados do Processo, ficheiro não bloqueado, justificação em falta e autoria. */
class ProcessoDocumentoServiceDesbloquearTest {

    private static final String JUSTIFICACAO = "A planta tinha a legenda desatualizada.";

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ProcessoDocumentoRepository repositorio = mock(ProcessoDocumentoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final ArmazenamentoDocumentos armazenamento = mock(ArmazenamentoDocumentos.class);
    private final UUID processoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final Processo processo = mock(Processo.class);
    private final ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, null, null,
            null, null, null, null, null, null);
    private ProcessoDocumento documento;
    private ProcessoDocumentoService servico;

    @BeforeEach
    void preparar() {
        when(processo.getId()).thenReturn(processoId);
        when(processo.getNumeroProcesso()).thenReturn("00001");
        when(processo.getEstado()).thenReturn(EstadoProcesso.EM_SUBMISSAO);
        Entidade entidade = mock(Entidade.class);
        when(entidade.getId()).thenReturn(7L);
        when(processo.getEntidade()).thenReturn(entidade);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(mock(InstrumentoProcedimentoConfig.class));
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(processo));
        when(processoRepository.findByIdComBloqueio(processoId)).thenReturn(Optional.of(processo));

        documento = new ProcessoDocumento(documentoId, processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "regulamento.pdf", "processos/00001/DEPOSITO/regulamento.pdf", "a".repeat(64), "v1", 10L, null);
        documento.setEstadoValidacao(EstadoValidacao.VALIDADO);
        documento.setBloqueadoEdicao(true);
        when(repositorio.findByIdAndProcesso_Id(documentoId, processoId)).thenReturn(Optional.of(documento));

        ContextoUtilizadorResolver resolver = mock(ContextoUtilizadorResolver.class);
        when(resolver.resolver()).thenReturn(contexto);
        ProcessoAlteravel processoAlteravel = new ProcessoAlteravel(processoRepository,
                mock(DocumentoConfigRepository.class), autorizacaoService);
        servico = new ProcessoDocumentoService(repositorio, processoAlteravel, mock(PlantaService.class),
                mock(MetadadosMotuService.class), mock(UtilizadorRepository.class), resolver, auditoriaService,
                armazenamento, mock(IntencaoArmazenamentoService.class), mock(LinkPublicoService.class),
                mock(VerificadorZip.class), mock(VerificadorTiff.class), mock(SubstituicaoDocumentosService.class),
                mock(ValidadorNomeSubstituicao.class), new GestorSemRecursos());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveDesbloquearGuardandoAJustificacaoSemEspacosEAuditarSemOTexto() {
        ProcessoDocumento resultado = servico.desbloquear(processoId, documentoId, "  " + JUSTIFICACAO + "\n");

        assertThat(resultado.isBloqueadoEdicao()).isFalse();
        assertThat(resultado.getJustificacaoDesbloqueio()).isEqualTo(JUSTIFICACAO);
        assertThat(resultado.getEstadoValidacao()).isEqualTo(EstadoValidacao.VALIDADO);
        ArgumentCaptor<Map<String, Object>> dados = ArgumentCaptor.forClass(Map.class);
        verify(auditoriaService).registar(eq("PROCESSO"), eq(processoId.toString()), eq("DESBLOQUEAR_DOCUMENTO"),
                eq(contexto.utilizadorId()), eq(7L), any(), dados.capture());
        assertThat(dados.getValue()).containsOnlyKeys("processoDocumentoId").containsEntry("processoDocumentoId", documentoId.toString());
        assertThat(dados.getValue().toString()).doesNotContain("legenda");
        verify(processo, never()).setEstado(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void deveRecusarAJustificacaoEmFaltaSemMudarNada(String justificacao) {
        assertThatThrownBy(() -> servico.desbloquear(processoId, documentoId, justificacao))
                .isInstanceOfSatisfying(JustificacaoEmFaltaException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("DOC_JUSTIFICACAO_EM_FALTA");
                    assertThat(e.getCampos()).singleElement().satisfies(campo -> {
                        assertThat(campo.path()).isEqualTo("justificacao");
                        assertThat(campo.erro()).isEqualTo("Indique a justificação do desbloqueio.");
                    });
                });

        assertThat(documento.isBloqueadoEdicao()).isTrue();
        assertThat(documento.getJustificacaoDesbloqueio()).isNull();
        verifyNoInteractions(auditoriaService);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = "EM_SUBMISSAO", mode = EnumSource.Mode.EXCLUDE)
    void deveRecusarForaDeEmSubmissao(EstadoProcesso estado) {
        when(processo.getEstado()).thenReturn(estado);

        assertThatThrownBy(() -> servico.desbloquear(processoId, documentoId, JUSTIFICACAO))
                .isInstanceOf(DocumentoEstadoInvalidoException.class)
                .hasMessage("Só é possível desbloquear um ficheiro depois de iniciar a correção.");

        assertThat(documento.isBloqueadoEdicao()).isTrue();
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void deveRecusarUmFicheiroQueNaoEstaBloqueado() {
        documento.setBloqueadoEdicao(false);

        assertThatThrownBy(() -> servico.desbloquear(processoId, documentoId, JUSTIFICACAO))
                .isInstanceOf(DocumentoEstadoInvalidoException.class)
                .hasMessage("O ficheiro não está bloqueado.");

        assertThat(documento.getJustificacaoDesbloqueio()).isNull();
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void deveRecusarQuemNaoEOAutor() {
        doThrow(new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la."))
                .when(autorizacaoService).verificarAutoria(contexto, processo);

        assertThatThrownBy(() -> servico.desbloquear(processoId, documentoId, JUSTIFICACAO))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        assertThat(documento.isBloqueadoEdicao()).isTrue();
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void deveDarNaoEncontradoParaUmDocumentoDeOutroProcesso() {
        UUID outro = UUID.randomUUID();
        when(repositorio.findByIdAndProcesso_Id(outro, processoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.desbloquear(processoId, outro, JUSTIFICACAO))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveRecusarARemocaoDeUmFicheiroDesbloqueado() {
        servico.desbloquear(processoId, documentoId, JUSTIFICACAO);

        assertThatThrownBy(() -> servico.remover(processoId, documentoId))
                .isInstanceOf(DocumentoEstadoInvalidoException.class)
                .hasMessage("Na correção, um ficheiro desbloqueado substitui-se; não se remove.");

        verify(repositorio, never()).delete(any(ProcessoDocumento.class));
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
