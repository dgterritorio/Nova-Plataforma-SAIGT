package com.conkord.saigt.dto.configuracao;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code tamanhoMaximoProcesso} em bytes; vazio = sem limite agregado. {@code visivelNaDinamica} vazio: ao criar, falso;
 * ao atualizar, mantém-se o gravado.
 */
public record ProcedimentoConfigRequest(
        @NotNull Long instrumentoDinamicaConfigId,
        @NotNull Long tipoProcedimentoId,
        @NotNull @Min(1) Integer ordem,
        @NotNull Boolean obrigatorio,
        @NotNull @Pattern(regexp = "PRIMEIRA|SEGUNDA_H|SEGUNDA_C", message = "Um de PRIMEIRA, SEGUNDA_H, SEGUNDA_C.")
        String serieDr,
        @Min(1) Long tamanhoMaximoProcesso,
        @NotNull Boolean repetivel,
        @NotNull Boolean atoPontual,
        @NotNull Boolean ativo,
        Boolean visivelNaDinamica) {}
