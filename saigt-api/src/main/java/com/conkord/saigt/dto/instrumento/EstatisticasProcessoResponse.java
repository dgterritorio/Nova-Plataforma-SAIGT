package com.conkord.saigt.dto.instrumento;

import java.util.List;

public record EstatisticasProcessoResponse(
        int numeroSubmissoes,
        List<RecusaResponse> recusas
) {
}
