package com.conkord.saigt.dto.documento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GravarObservacoesRequest(
        @NotNull @Size(max = 1000) String observacoes
) {
}
