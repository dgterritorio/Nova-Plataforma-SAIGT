package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.TipoInstrumento;

public record TipoInstrumentoConfigResponse(Long id, String codigo, String nome, String categoria, boolean ativo) {

    public static TipoInstrumentoConfigResponse de(TipoInstrumento tipo) {
        return new TipoInstrumentoConfigResponse(tipo.getId(), tipo.getCodigo(), tipo.getNome(), tipo.getCategoria(),
                tipo.isAtivo());
    }
}
