package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.Size;

public record LocalizacaoRequest(
        Boolean aplicavelTodaArea,
        @Size(max = 20) String freguesia,
        @Size(max = 20) String toponimoCodigo,
        @Size(max = 500) String toponimoTexto
) {
}
