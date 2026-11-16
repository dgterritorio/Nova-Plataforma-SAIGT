package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** `idIgt`, `publicationOrder` e `activeDynamicsCount` vêm vazios na primeira publicação. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicacaoRecebidaSnit(
        Long ssaigtId,
        Instant receivedAt,
        Long idIgt,
        Integer publicationOrder,
        Integer activeDynamicsCount
) {
}
