package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;

/** CIM / AM ativa para o formulário SGIFR; `regiao` é o `codigo` da Região de que depende, para o filtro no ecrã. */
public record OpcaoCimAmResponse(String codigo, String descricao, String regiao) {

    public static OpcaoCimAmResponse de(ListaValores item) {
        return new OpcaoCimAmResponse(item.getCodigo(), item.getDescricao(),
                item.getPai() == null ? null : item.getPai().getCodigo());
    }
}
