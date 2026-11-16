package com.conkord.saigt.dto.relatorio;

import java.util.List;
import java.util.UUID;

public record EstatisticasDinamicaResponse(
        UUID dinamicaId,
        int numeroRejeicoesDgt,
        int numeroAnulacoesIncm,
        Integer numeroCiclosResubmissao,
        Integer numeroComunicacoes,
        List<EstatisticasProcessoDinamicaResponse> processos
) {
}
