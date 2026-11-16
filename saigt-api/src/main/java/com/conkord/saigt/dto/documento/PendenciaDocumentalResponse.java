package com.conkord.saigt.dto.documento;

public record PendenciaDocumentalResponse(
        Long documentoConfigId,
        String codigo,
        String nome,
        String categoria,
        String motivo
) {
}
