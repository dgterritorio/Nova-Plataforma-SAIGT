package com.conkord.saigt.dto.listavalores;

import java.util.List;

/** Catálogo global (§11) para o ecrã "Listas de Valores". */
public record CatalogoResponse(String titulo, List<String> itens) {
}
