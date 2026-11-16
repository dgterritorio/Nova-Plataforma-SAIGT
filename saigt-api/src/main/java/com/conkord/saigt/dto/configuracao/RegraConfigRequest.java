package com.conkord.saigt.dto.configuracao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Exatamente um dos dois `…ConfigId` (BL-PROC-044), verificado no serviço. */
public record RegraConfigRequest(
        Long instrumentoDinamicaConfigId,
        Long instrumentoProcedimentoConfigId,
        @NotNull Boolean substituiSequenciamento,
        @NotNull @Pattern(regexp = "AND|OR", message = "Tem de ser AND ou OR.") String operador,
        @Size(max = 300) String descricao,
        @NotNull Boolean ativo,
        @NotNull List<@NotNull @Valid CondicaoConfigRequest> condicoes) {}
