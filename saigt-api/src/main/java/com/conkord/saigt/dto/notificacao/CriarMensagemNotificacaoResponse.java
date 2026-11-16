package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.EstadoNotificacao;

public record CriarMensagemNotificacaoResponse(MensagemNotificacaoResponse mensagem, EstadoNotificacao estado) {
}
