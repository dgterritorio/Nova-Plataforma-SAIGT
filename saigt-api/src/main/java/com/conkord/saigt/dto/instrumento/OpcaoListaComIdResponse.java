package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;

/** Item de uma lista de valores com o `id`, para os campos que gravam a referência ao item. */
public record OpcaoListaComIdResponse(Long id, String codigo, String descricao) {

    public static OpcaoListaComIdResponse de(ListaValores item) {
        return new OpcaoListaComIdResponse(item.getId(), item.getCodigo(), item.getDescricao());
    }
}
