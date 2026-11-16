package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;

import java.util.UUID;

/** Uma Dinâmica que o processo pode associar, com o Instrumento associável que abrange. */
public record DinamicaAssociavelResponse(
        UUID dinamicaId,
        String numeroDinamica,
        String descritivo,
        /** Código do tipo de Dinâmica. */
        String tipoDinamica,
        /** `EM_CURSO` ou `CADUCADA` na lista; no detalhe, o estado atual da Dinâmica associada. */
        String estado,
        InstrumentoAssociavel instrumento
) {

    public record InstrumentoAssociavel(UUID id, String designacao, String tipoInstrumento) {
    }

    /** `instrumento` nulo dá `instrumento` nulo na resposta. */
    public static DinamicaAssociavelResponse de(Dinamica dinamica, Instrumento instrumento) {
        return new DinamicaAssociavelResponse(dinamica.getId(), dinamica.getNumeroDinamica(), dinamica.getDescritivo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(), dinamica.getEstado().name(),
                instrumento == null ? null : new InstrumentoAssociavel(instrumento.getId(), instrumento.getDesignacao(),
                        instrumento.getTipoInstrumento().getCodigo()));
    }
}
