package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `criadoPor` vem sempre do token, nunca do corpo. */
public record CriarDinamicaRequest(
        @NotNull Long instrumentoDinamicaConfigId,
        // Distingue a Dinâmica e aparece em todos os seus processos.
        @NotBlank @Size(max = 50) String descritivo
) {
}
