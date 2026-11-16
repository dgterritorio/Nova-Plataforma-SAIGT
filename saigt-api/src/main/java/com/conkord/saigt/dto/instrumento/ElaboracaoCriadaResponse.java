package com.conkord.saigt.dto.instrumento;

import java.util.UUID;

public record ElaboracaoCriadaResponse(
        UUID instrumentoId,
        UUID dinamicaId,
        ProcessoResponse processo
) {
}
