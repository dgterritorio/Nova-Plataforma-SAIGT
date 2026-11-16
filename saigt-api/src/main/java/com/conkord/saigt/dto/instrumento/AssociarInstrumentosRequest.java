package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** O conjunto completo dos Instrumentos que a Dinâmica passa a abranger; vazio desassocia todos. */
public record AssociarInstrumentosRequest(
        @NotNull @Size(max = 200) List<@NotNull UUID> instrumentoIds
) {
}
