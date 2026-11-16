package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Sem `motivo`: o que um cliente antigo ainda envie é ignorado na leitura do JSON e nunca gravado.
 * `pedidoApoioId` é opcional: o pedido de apoio da entidade que esta transferência resolve.
 */
public record ReatribuirAutorRequest(
        @NotNull Long versao,
        @NotNull UUID novoUtilizadorId,
        UUID pedidoApoioId
) {
}
