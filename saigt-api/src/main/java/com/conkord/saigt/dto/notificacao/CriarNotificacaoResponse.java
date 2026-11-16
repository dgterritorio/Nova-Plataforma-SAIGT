package com.conkord.saigt.dto.notificacao;

import java.util.List;
import java.util.UUID;

public record CriarNotificacaoResponse(List<NotificacaoResumoResponse> notificacoes, UUID loteId) {
}
