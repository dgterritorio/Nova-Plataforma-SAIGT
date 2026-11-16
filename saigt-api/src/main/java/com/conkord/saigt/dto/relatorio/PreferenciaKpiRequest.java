package com.conkord.saigt.dto.relatorio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PreferenciaKpiRequest(
        @NotBlank @Size(max = 60) String kpiCodigo,
        @NotNull Boolean visivel,
        @NotNull @Min(0) @Max(32767) Integer ordem
) {
}
