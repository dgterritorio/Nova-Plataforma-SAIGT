package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;

import java.time.Instant;
import java.util.UUID;

public record DocumentoNotificacaoResponse(UUID id, String nomeFicheiro, long tamanhoBytes, Instant criadoEm) {

    public static DocumentoNotificacaoResponse de(NotificacaoDocumento documento) {
        return new DocumentoNotificacaoResponse(documento.getId(), documento.getNomeFicheiro(),
                documento.getTamanhoBytes(), documento.getCriadoEm());
    }
}
