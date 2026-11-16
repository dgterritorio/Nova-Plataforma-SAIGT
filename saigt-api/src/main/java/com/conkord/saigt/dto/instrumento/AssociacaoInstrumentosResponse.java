package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;

import java.util.List;

/** Os Instrumentos que a Dinâmica abrange depois da associação, pela ordem de `instrumentosAbrangidos`. */
public record AssociacaoInstrumentosResponse(List<InstrumentoAbrangidoResponse> instrumentos) {

    public static AssociacaoInstrumentosResponse de(Dinamica dinamica) {
        return new AssociacaoInstrumentosResponse(InstrumentoAbrangidoResponse.de(dinamica));
    }
}
