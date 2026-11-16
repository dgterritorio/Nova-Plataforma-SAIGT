package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DesbloquearCampoRequest(
        @NotNull Long versao,
        @NotBlank(message = "Indique o campo a desbloquear.") String campo,
        @NotBlank(message = "Indique o motivo do desbloqueio.") String motivo
) {
}
