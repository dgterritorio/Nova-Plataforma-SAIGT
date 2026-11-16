package com.conkord.saigt.dto.instrumento;

import java.util.List;

public record TerritorioInstrumentoResponse(
        List<ItemTerritorio> concelhos,
        List<ItemTerritorio> nutsII
) {

    public static TerritorioInstrumentoResponse vazio() {
        return new TerritorioInstrumentoResponse(List.of(), List.of());
    }

    public record ItemTerritorio(String codigo, String nome) {
    }
}
