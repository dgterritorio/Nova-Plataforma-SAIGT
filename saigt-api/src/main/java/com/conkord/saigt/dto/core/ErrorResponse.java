package com.conkord.saigt.dto.core;

import java.time.OffsetDateTime;
import java.util.List;

/** Envelope de erro, formato exato de §22.1. */
public record ErrorResponse(
        String codigo,
        String mensagem,
        OffsetDateTime timestamp,
        String traceId,
        List<CampoErro> campos
) {
    public record CampoErro(String path, String erro) {}
}
