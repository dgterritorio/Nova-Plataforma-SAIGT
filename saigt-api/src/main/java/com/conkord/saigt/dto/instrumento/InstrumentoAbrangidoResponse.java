package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Um Instrumento que a Dinâmica do processo abrange (`dinamica_instrumento`). */
public record InstrumentoAbrangidoResponse(UUID id, String designacao, String tipoInstrumento) {

    /** O Instrumento de início primeiro, depois por designação; vazia numa Dinâmica ainda sem Instrumentos. */
    public static List<InstrumentoAbrangidoResponse> de(Dinamica dinamica) {
        UUID inicio = dinamica.instrumentoDeInicio().map(Instrumento::getId).orElse(null);
        return dinamica.getInstrumentos().stream()
                .sorted(Comparator.comparing((Instrumento i) -> !i.getId().equals(inicio))
                        .thenComparing(Instrumento::getDesignacao, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Instrumento::getId))
                .map(i -> new InstrumentoAbrangidoResponse(i.getId(), i.getDesignacao(), i.getTipoInstrumento().getCodigo()))
                .toList();
    }
}
