package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Instrumento;

import java.util.UUID;

/** Referência curta a outro Instrumento, como a servidão de origem; lida com a associação carregada. */
public record InstrumentoReferenciaResponse(UUID id, String designacao) {

    public static InstrumentoReferenciaResponse de(Instrumento instrumento) {
        return instrumento == null ? null : new InstrumentoReferenciaResponse(instrumento.getId(), instrumento.getDesignacao());
    }
}
