package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;

public record RejeitarProcessoRequest(
        @NotNull Long versao,
        String notasGerais
) {
}
