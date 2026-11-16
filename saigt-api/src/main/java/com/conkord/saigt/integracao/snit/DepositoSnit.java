package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Pedido de registo do depósito no SNIT (`SaigtDepositInboundDto`); `ssaigtId` é o n.º de processo SAIGT. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepositoSnit(
        Long ssaigtId,
        String depositNumber,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant depositedAt,
        String idus,
        String downloadUrl,
        VerificacaoSnit verification
) {
}
