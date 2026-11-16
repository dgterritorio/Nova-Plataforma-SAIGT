package com.conkord.saigt.dto.relatorio;

import java.util.UUID;

public record EstatisticasProcessoDinamicaResponse(
        UUID processoId,
        String numeroProcesso,
        String tipoProcedimento,
        int ocorrencia,
        int numeroRejeicoesDgt,
        int numeroAnulacoesIncm,
        Integer numeroCiclosResubmissao,
        Integer numeroComunicacoes
) {
}
