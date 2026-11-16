package com.conkord.saigt.dto.instrumento;

import java.util.List;

/** `id` é o `documentoConfigId` do upload. */
public record DocumentoExigidoResponse(Long id, String nome, String categoria, boolean obrigatorio,
                                       List<String> extensoesAceites) {
}
