package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;

/** Item ativo de uma lista de valores, para escolha num formulário. */
public record OpcaoListaResponse(String codigo, String descricao) {

    public static OpcaoListaResponse de(ListaValores item) {
        return new OpcaoListaResponse(item.getCodigo(), item.getDescricao());
    }
}
