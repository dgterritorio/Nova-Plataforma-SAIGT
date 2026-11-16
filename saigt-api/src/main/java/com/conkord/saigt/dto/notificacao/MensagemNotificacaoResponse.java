package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.NotificacaoMensagem;

import java.time.Instant;
import java.util.UUID;

public record MensagemNotificacaoResponse(UUID id, UUID autorId, String autorNome, String texto, Instant criadoEm) {

    public static MensagemNotificacaoResponse de(NotificacaoMensagem mensagem) {
        return new MensagemNotificacaoResponse(mensagem.getId(), mensagem.getAutor().getId(),
                mensagem.getAutor().getNome(), mensagem.getTexto(), mensagem.getCriadoEm());
    }
}
