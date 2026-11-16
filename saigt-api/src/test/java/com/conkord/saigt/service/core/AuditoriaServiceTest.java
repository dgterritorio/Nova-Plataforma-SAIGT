package com.conkord.saigt.service.core;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditoriaServiceTest {

    private final AuditoriaRepository repository = mock(AuditoriaRepository.class);
    private final AuditoriaService service = new AuditoriaService(repository, new ObjectMapper());

    @Test
    void deveRegistarAuditoriaComDadosSerializados() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Auditoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID utilizadorId = UUID.randomUUID();

        service.registar("PROCESSO", "42", "PROCESSO_ENVIADO_DGT", utilizadorId, Map.of("estado", "ENVIADO_DGT"));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).save(captor.capture());
        Auditoria guardada = captor.getValue();
        assertThat(guardada.getTipoOcorrencia()).isEqualTo("PROCESSO");
        assertThat(guardada.getIdOcorrencia()).isEqualTo("42");
        assertThat(guardada.getAcao()).isEqualTo("PROCESSO_ENVIADO_DGT");
        assertThat(guardada.getUtilizadorId()).isEqualTo(utilizadorId);
        assertThat(guardada.getDadosAlteracao()).contains("\"estado\":\"ENVIADO_DGT\"");
    }

    @Test
    void deveRegistarAuditoriaSemDados() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Auditoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registar("UTILIZADOR", "u-1", "LOGIN", UUID.randomUUID(), null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDadosAlteracao()).isNull();
    }

    @Test
    void naoDeveGravarNadaQuandoUtilizadorIdENulo() {
        Auditoria resultado = service.registar("UTILIZADOR", "desconhecido@dgt.pt", "LOGIN_RECUSADO", null,
                "127.0.0.1", null);

        assertThat(resultado).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void deveGravarAcaoDoSistemaSemUtilizador() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Auditoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registarSistema("OBJETO_STORAGE", "abc-anexo.pdf", "REMOVER_OBJETO_ORFAO",
                Map.of("caminho", "privado/x/abc-anexo.pdf"));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUtilizadorId()).isNull();
        assertThat(captor.getValue().getAcao()).isEqualTo("REMOVER_OBJETO_ORFAO");
        assertThat(captor.getValue().getDadosAlteracao()).contains("privado/x/abc-anexo.pdf");
    }

    @Test
    void deveGravarOEstadoAntesEDepoisNumaAcaoDoSistemaSemUtilizador() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Auditoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registarSistema("PROCESSO", "42", "REJEICAO_INCM",
                AuditoriaService.TransicaoEstado.de(EstadoProcesso.EM_VALIDACAO_INCM, EstadoProcesso.ANULADO_INCM),
                Map.of("mensagens", List.of("Entidade inexistente.")));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).save(captor.capture());
        Auditoria guardada = captor.getValue();
        assertThat(guardada.getUtilizadorId()).isNull();
        assertThat(guardada.getAcao()).isEqualTo("REJEICAO_INCM");
        assertThat(guardada.getEstadoAntes()).isEqualTo("EM_VALIDACAO_INCM");
        assertThat(guardada.getEstadoDepois()).isEqualTo("ANULADO_INCM");
        assertThat(guardada.getDadosAlteracao()).contains("Entidade inexistente.");
    }

    @Test
    void deveDeixarOEstadoVazioNaAcaoDoSistemaSemTransicao() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Auditoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registarSistema("OBJETO_STORAGE", "abc.pdf", "REMOVER_OBJETO_ORFAO", null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEstadoAntes()).isNull();
        assertThat(captor.getValue().getEstadoDepois()).isNull();
    }
}
