package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.Size;

public record MotivoAlteracaoRequest(
        @Size(max = 40) String tipo,
        @Size(max = 20) String codigo
) {
}
