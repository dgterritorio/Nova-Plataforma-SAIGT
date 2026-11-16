package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BloqueioDocumentosCorrecaoTest {

    private final ProcessoDocumentoRepository repository = mock(ProcessoDocumentoRepository.class);
    private final BloqueioDocumentosCorrecao bloqueio = new BloqueioDocumentosCorrecao(repository);

    private final UUID processoId = UUID.randomUUID();
    private final Processo processo = processo(processoId);

    private static Processo processo(UUID id) {
        Processo processo = new Processo();
        ReflectionTestUtils.setField(processo, "id", id);
        return processo;
    }

    @Test
    void deveBloquearSoOsValidadosQuandoVemDeRejeitadoDgtEDevolverOsBloqueados() {
        when(repository.bloquearSoValidados(processoId, EstadoValidacao.VALIDADO)).thenReturn(3);
        when(repository.contarBloqueadosAtuais(processoId)).thenReturn(1L);

        long bloqueados = bloqueio.bloquearParaCorrecao(processo, EstadoProcesso.REJEITADO_DGT);

        assertThat(bloqueados).isEqualTo(1L);
        verify(repository).bloquearSoValidados(processoId, EstadoValidacao.VALIDADO);
        verify(repository, never()).bloquearTodos(processoId);
    }

    @Test
    void deveBloquearTodosQuandoVemDeAnuladoIncm() {
        when(repository.bloquearTodos(processoId)).thenReturn(3);
        when(repository.contarBloqueadosAtuais(processoId)).thenReturn(3L);

        long bloqueados = bloqueio.bloquearParaCorrecao(processo, EstadoProcesso.ANULADO_INCM);

        assertThat(bloqueados).isEqualTo(3L);
        verify(repository).bloquearTodos(processoId);
        verify(repository, never()).bloquearSoValidados(processoId, EstadoValidacao.VALIDADO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"}, mode = EnumSource.Mode.EXCLUDE)
    void deveRecusarOrigemSemRegraDeBloqueio(EstadoProcesso origem) {
        assertThatThrownBy(() -> bloqueio.bloquearParaCorrecao(processo, origem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(origem.name());

        verifyNoInteractions(repository);
    }
}
