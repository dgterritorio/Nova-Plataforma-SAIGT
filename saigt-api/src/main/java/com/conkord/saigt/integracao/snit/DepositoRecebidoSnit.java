package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** `existingTask` verdadeiro quando o SNIT ligou o depósito a uma tarefa que já existia. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DepositoRecebidoSnit(
        Long ssaigtId,
        String depositNumber,
        Long availabilityTaskId,
        Boolean existingTask,
        String processStatus,
        String phase,
        Long idIgt,
        Integer activeDynamicsCount
) {
}
