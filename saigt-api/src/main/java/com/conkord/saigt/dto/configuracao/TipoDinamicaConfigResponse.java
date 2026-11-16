package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;

public record TipoDinamicaConfigResponse(Long id, String codigo, String nome, boolean ativo) {

    public static TipoDinamicaConfigResponse de(TipoDinamica tipo) {
        return new TipoDinamicaConfigResponse(tipo.getId(), tipo.getCodigo(), tipo.getNome(), tipo.isAtivo());
    }
}
