package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.TipoProcedimento;

/** {@code formulario}: nome do formulário fixo do tipo, ou nulo quando ainda não há desenvolvimento para ele. */
public record TipoProcedimentoConfigResponse(Long id, String codigo, String nome, boolean ativo, String formulario) {

    public static TipoProcedimentoConfigResponse de(TipoProcedimento tipo, String formulario) {
        return new TipoProcedimentoConfigResponse(tipo.getId(), tipo.getCodigo(), tipo.getNome(), tipo.isAtivo(), formulario);
    }
}
