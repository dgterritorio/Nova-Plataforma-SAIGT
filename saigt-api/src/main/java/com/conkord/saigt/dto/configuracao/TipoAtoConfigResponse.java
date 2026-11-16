package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.TipoAto;

public record TipoAtoConfigResponse(Long id, String codigo, String nome, Long incmActTypeIdSerie1,
                                    Long incmActTypeIdSerie2, int ordem, boolean ativo) {

    public static TipoAtoConfigResponse de(TipoAto tipo) {
        return new TipoAtoConfigResponse(tipo.getId(), tipo.getCodigo(), tipo.getNome(), tipo.getIncmActTypeIdSerie1(),
                tipo.getIncmActTypeIdSerie2(), tipo.getOrdem(), tipo.isAtivo());
    }
}
