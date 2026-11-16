package com.conkord.saigt.dto.instrumento;

import java.util.UUID;

/** `instrumentoId` só vem preenchido quando o IGT já existe na SAIGT e é visível para a sessão. */
public record IgtSnitResponse(
        String idIgtSnit,
        String designacao,
        String tipoInstrumento,
        UUID instrumentoId
) {
}
