package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnularProcessoRequest(
        @NotNull Long versao,
        @NotBlank String motivo
) {
}
