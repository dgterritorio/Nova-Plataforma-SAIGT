package com.conkord.saigt.dto.configuracao;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Com `id`, edita essa condição da mesma regra; sem ele, cria uma nova. */
public record CondicaoConfigRequest(
        Long id,
        @NotBlank String avaliador,
        @NotNull JsonNode parametros,
        @NotNull @Min(1) Short grupo,
        @NotNull Boolean ativo) {}
