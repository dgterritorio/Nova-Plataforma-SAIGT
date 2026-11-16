package com.conkord.saigt.dto.instrumento;

public record LocalizacaoResponse(
        Boolean aplicavelTodaArea,
        OpcaoListaResponse freguesia,
        OpcaoListaResponse toponimo,
        String toponimoTexto
) {
}
