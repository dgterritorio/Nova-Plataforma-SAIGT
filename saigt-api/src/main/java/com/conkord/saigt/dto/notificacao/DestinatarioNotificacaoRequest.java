package com.conkord.saigt.dto.notificacao;

import java.util.UUID;

public record DestinatarioNotificacaoRequest(Long entidadeId, UUID utilizadorId) {

    public boolean temExatamenteUmDestino() {
        return (entidadeId == null) != (utilizadorId == null);
    }
}
