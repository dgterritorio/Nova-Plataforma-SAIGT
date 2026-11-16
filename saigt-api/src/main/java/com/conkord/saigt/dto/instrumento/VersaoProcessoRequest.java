package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;

/** Corpo das transições que não têm mais nada a enviar. */
public record VersaoProcessoRequest(
        @NotNull Long versao
) {
}
