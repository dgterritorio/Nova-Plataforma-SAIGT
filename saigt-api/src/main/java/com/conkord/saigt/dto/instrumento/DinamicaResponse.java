package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * `instrumentoId` é o Instrumento de início, nulo numa Dinâmica iniciada sem IGT; `instrumentoIds` os que a Dinâmica
 * abrange, o de início primeiro.
 */
public record DinamicaResponse(
        UUID id,
        String numeroDinamica,
        String descritivo,
        int tentativa,
        UUID instrumentoId,
        List<UUID> instrumentoIds,
        Long instrumentoDinamicaConfigId,
        String tipoDinamica,
        String nomeDinamica,
        String estado,
        String origemDados,
        Instant dataInicio,
        Instant dataConclusao,
        UUID criadoPorId
) {
    public static DinamicaResponse de(Dinamica dinamica) {
        UUID instrumentoDeInicio = dinamica.instrumentoDeInicio().map(Instrumento::getId).orElse(null);
        return new DinamicaResponse(
                dinamica.getId(),
                dinamica.getNumeroDinamica(),
                dinamica.getDescritivo(),
                dinamica.getTentativa(),
                instrumentoDeInicio,
                dinamica.getInstrumentos().stream()
                        .map(Instrumento::getId)
                        .sorted(Comparator.comparing((UUID id) -> !id.equals(instrumentoDeInicio))
                                .thenComparing(Comparator.naturalOrder()))
                        .toList(),
                dinamica.getInstrumentoDinamicaConfig().getId(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(),
                dinamica.getEstado().name(),
                dinamica.getOrigemDados(),
                dinamica.getDataInicio(),
                dinamica.getDataConclusao(),
                dinamica.getCriadoPor().getId());
    }
}
