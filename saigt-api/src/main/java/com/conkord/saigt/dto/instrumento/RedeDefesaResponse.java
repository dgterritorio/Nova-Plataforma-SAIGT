package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.SgifrRedeDefesa;

public record RedeDefesaResponse(String codigo, String nome) {

    public static RedeDefesaResponse de(SgifrRedeDefesa rede) {
        return new RedeDefesaResponse(rede.getCodigo(), rede.getNome());
    }
}
