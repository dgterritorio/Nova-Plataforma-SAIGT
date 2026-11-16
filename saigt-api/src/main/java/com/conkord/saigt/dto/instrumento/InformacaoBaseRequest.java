package com.conkord.saigt.dto.instrumento;

import jakarta.validation.Valid;

/** Os dois elementos são opcionais: é um rascunho. */
public record InformacaoBaseRequest(
        @Valid InformacaoBaseDeliberacaoRequest deliberacao,
        @Valid InformacaoBaseTextoAtoRequest textoAto
) {
}
