package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * `itemId` é o `processo_documento.id` ou o `processo_informacao_base.id`, conforme o `tipo`. `categoriaId`,
 * `motivoId` e `notas` só contam com `decisao = REJEITADO`; com `VALIDADO` são ignorados.
 */
public record ItemValidacaoRequest(
        @NotNull TipoItemValidacao tipo,
        @NotNull UUID itemId,
        @NotNull EstadoValidacao decisao,
        Long categoriaId,
        Long motivoId,
        String notas
) {
}
