package com.conkord.saigt.service.idus;

import java.util.List;
import java.util.UUID;

/**
 * O que a entrada em vigor deixou para propagar. `nova` é falso numa repetição com os mesmos dados, que não se
 * propaga outra vez. `idusDinamica` é nulo quando o processo da publicação final não tem IDUS; `ssaigtId` é o n.º do
 * processo da publicação final, que identifica o depósito no SNIT.
 */
public record EntradaEmVigorRegistada(
        String numeroDeposito,
        boolean nova,
        UUID processoFinalId,
        Long ssaigtId,
        String idusDinamica,
        List<UUID> processos
) {

    public EntradaEmVigorRegistada {
        processos = processos == null ? List.of() : List.copyOf(processos);
    }

    static EntradaEmVigorRegistada repetida(String numeroDeposito, UUID processoFinalId) {
        return new EntradaEmVigorRegistada(numeroDeposito, false, processoFinalId, null, null, List.of());
    }
}
