package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;

/** Um valor de `MOTIVO_PROCEDIMENTO_REN`; o `id` é o que o formulário envia em `motivoProcedimentoId`. */
public record MotivoProcedimentoResponse(Long id, String codigo, String descricao) {

    public static MotivoProcedimentoResponse de(ListaValores valor) {
        return new MotivoProcedimentoResponse(valor.getId(), valor.getCodigo(), valor.getDescricao());
    }
}
