package com.conkord.saigt.dto.documento;

import jakarta.validation.constraints.Size;

/** A justificação em branco ou ausente dá `DOC_JUSTIFICACAO_EM_FALTA` no serviço, não o 400 genérico. */
public record DesbloquearDocumentoRequest(
        @Size(max = 2000) String justificacao
) {
}
