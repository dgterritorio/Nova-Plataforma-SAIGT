package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;

public record ValorListaResponse(String codigo, String descricao) {

    public static ValorListaResponse de(ListaValores valor) {
        return valor == null ? null : new ValorListaResponse(valor.getCodigo(), valor.getDescricao());
    }
}
