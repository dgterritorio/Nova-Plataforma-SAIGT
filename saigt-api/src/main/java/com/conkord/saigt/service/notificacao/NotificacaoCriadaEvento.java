package com.conkord.saigt.service.notificacao;

import java.util.List;
import java.util.UUID;

/**
 * Um por lote, para a cópia por email ser enviada uma vez e não uma vez por linha. Com `emailContactoTramitacao`, o
 * email vai uma só vez para o contacto de tramitação do processo das linhas, qualquer que seja o destinatário delas.
 */
public record NotificacaoCriadaEvento(List<UUID> notificacaoIds, UUID loteId, boolean emailContactoTramitacao) {
}
