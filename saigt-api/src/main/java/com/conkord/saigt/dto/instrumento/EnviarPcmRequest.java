package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** O utilizador PCM a quem o processo fica atribuído (`pcm_atribuido_a`). */
public record EnviarPcmRequest(
        @NotNull Long versao,
        @NotNull UUID utilizadorId
) {
}
