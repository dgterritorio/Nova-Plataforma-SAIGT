package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** `documentoConfigId` nulo = balde «outros» do depósito; `tags` substitui as atuais e pode vir vazia. */
public record ReclassificarDocumentoRequest(
        @NotNull CategoriaDocumento categoria,
        Long documentoConfigId,
        @NotNull List<Long> tags
) {
}
