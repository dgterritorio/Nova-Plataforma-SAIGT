package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.SgifrRedeDefesa;

public record RedeDefesaConfigResponse(Long id, String codigo, String nome, boolean aplicaPra, boolean aplicaPsa,
                                       boolean publicavel) {

    public static RedeDefesaConfigResponse de(SgifrRedeDefesa rede) {
        return new RedeDefesaConfigResponse(rede.getId(), rede.getCodigo(), rede.getNome(), rede.isAplicaPra(),
                rede.isAplicaPsa(), rede.isPublicavel());
    }
}
