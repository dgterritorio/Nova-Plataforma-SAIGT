package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Já filtrado pela série e por `atos_autorizados` do utilizador.
 * Só referência para o FE: a autorização decide-se de novo na submissão.
 */
public record TipoAtoDisponivelResponse(
        Long id,
        String codigo,
        String nome,
        /** Só no `/disponivel` dos Procedimentos sem série configurada: as séries em que o tipo existe para a sessão. */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> seriesDr
) {
    public TipoAtoDisponivelResponse(Long id, String codigo, String nome) {
        this(id, codigo, nome, null);
    }
}
