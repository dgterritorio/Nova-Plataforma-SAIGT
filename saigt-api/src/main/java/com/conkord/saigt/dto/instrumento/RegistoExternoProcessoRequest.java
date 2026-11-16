package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ao contrário do fluxo normal (preenchido pela INCM), `numeroDr` e `dataPublicacaoDr` são obrigatórios.
 * Com `notificacaoOrigemId`, a regularização ou a resposta obrigatória fica resolvida e ligada ao processo, e os seus
 * anexos são copiados para o depósito do processo.
 */
public record RegistoExternoProcessoRequest(
        @NotNull Long instrumentoProcedimentoConfigId,
        /** A entidade a que o processo passa a pertencer: o `MASTER` não tem entidade de sessão. */
        @NotNull Long entidadeId,
        @NotBlank String numeroDr,
        @NotNull LocalDate dataPublicacaoDr,
        @NotBlank String motivo,
        UUID notificacaoOrigemId
) {
}
