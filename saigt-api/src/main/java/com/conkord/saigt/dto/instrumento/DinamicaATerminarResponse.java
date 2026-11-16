package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;

import java.util.UUID;

/** Uma Dinâmica em curso que a Revogação do IGT declara terminar; `tipoDinamica` é o nome, para apresentação. */
public record DinamicaATerminarResponse(UUID dinamicaId, String numeroDinamica, String tipoDinamica, String descritivo) {

    public static DinamicaATerminarResponse de(Dinamica dinamica) {
        return new DinamicaATerminarResponse(dinamica.getId(), dinamica.getNumeroDinamica(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(), dinamica.getDescritivo());
    }
}
