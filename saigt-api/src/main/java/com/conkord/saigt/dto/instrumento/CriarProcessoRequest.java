package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * `criadoPor` vem sempre do token, nunca do corpo. `tipoAtoId` é opcional: o processo pode nascer sem tipo de ato, que
 * se escolhe no cabeçalho e é obrigatório para submeter. `processoReferidoId` é obrigatório nos Procedimentos
 * `ato_pontual` e proibido nos outros.
 */
public record CriarProcessoRequest(
        @NotNull Long instrumentoProcedimentoConfigId,
        Long tipoAtoId,
        UUID processoReferidoId
) {
    public CriarProcessoRequest(Long instrumentoProcedimentoConfigId) {
        this(instrumentoProcedimentoConfigId, null, null);
    }

    public CriarProcessoRequest(Long instrumentoProcedimentoConfigId, Long tipoAtoId) {
        this(instrumentoProcedimentoConfigId, tipoAtoId, null);
    }
}
