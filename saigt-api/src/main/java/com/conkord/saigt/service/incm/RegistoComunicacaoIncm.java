package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.FalhaTecnicaIncm;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;

import java.time.Instant;
import java.util.UUID;


public record RegistoComunicacaoIncm(
        UUID processoId,
        OperacaoIncm operacao,
        Long actIdentifier,
        int submissao,
        String serieDr,
        Object pedido,
        Object resposta,
        Integer httpStatus,
        Boolean isSuccess,
        String processingStatus,
        String actStatus,
        FalhaTecnicaIncm falhaTecnica,
        EstadoProcesso transicaoDerivada,
        String correlationId,
        Instant enviadoEm,
        Instant recebidoEm
) {
}
