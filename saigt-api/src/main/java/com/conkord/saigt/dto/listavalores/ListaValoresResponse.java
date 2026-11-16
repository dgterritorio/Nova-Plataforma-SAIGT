package com.conkord.saigt.dto.listavalores;

import com.conkord.saigt.persistence.core.ListaValores;

public record ListaValoresResponse(Long id, String tipo, String codigo, String descricao, int ordem, boolean ativo,
                                   Long paiId) {

    public static ListaValoresResponse de(ListaValores item) {
        return new ListaValoresResponse(item.getId(), item.getTipo(), item.getCodigo(), item.getDescricao(),
                item.getOrdem(), item.isAtivo(), item.getPai() == null ? null : item.getPai().getId());
    }
}
