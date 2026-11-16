package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** As observações vão só para a auditoria, nunca para os logs. */
public record DevolverDgtRequest(
        @NotNull Long versao,
        @Size(max = 2000) String observacoes
) {
}
