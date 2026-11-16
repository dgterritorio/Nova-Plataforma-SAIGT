package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FicheirosRecebidosSnit(
        Long ssaigtId,
        Integer fileCount
) {
}
