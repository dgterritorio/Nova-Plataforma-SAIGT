package com.conkord.saigt.dto.notificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarMensagemNotificacaoRequest(@NotBlank @Size(max = 5000) String texto) {
}
