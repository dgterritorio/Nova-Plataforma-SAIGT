package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Início de uma Dinâmica sem IGT de partida (`POST /api/v1/dinamicas`): os Instrumentos associam-se depois do
 * formulário inicial. `criadoPor` vem sempre do token.
 */
public record IniciarDinamicaRequest(
        @NotNull Long instrumentoDinamicaConfigId,
        // A Designação da Dinâmica nos tipos com `designacao_unica`.
        @NotBlank @Size(max = 50) String descritivo
) {
}
