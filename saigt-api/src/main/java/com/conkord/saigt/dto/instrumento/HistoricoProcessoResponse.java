package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.Auditoria;

import java.time.Instant;
import java.util.UUID;

/** Uma entrada de `auditoria`. `utilizadorNome` é nulo nas ações do sistema. */
public record HistoricoProcessoResponse(
        Long id,
        String acao,
        String estadoAntes,
        String estadoDepois,
        UUID utilizadorId,
        String utilizadorNome,
        String dadosAlteracao,
        Instant criadoEm
) {
    public static HistoricoProcessoResponse de(Auditoria auditoria, String utilizadorNome) {
        return new HistoricoProcessoResponse(
                auditoria.getId(),
                auditoria.getAcao(),
                auditoria.getEstadoAntes(),
                auditoria.getEstadoDepois(),
                auditoria.getUtilizadorId(),
                utilizadorNome,
                auditoria.getDadosAlteracao(),
                auditoria.getCriadoEm());
    }
}
