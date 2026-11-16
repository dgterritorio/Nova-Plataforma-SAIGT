package com.conkord.saigt.dto.configuracao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TipoInstrumentoConfigRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Só maiúsculas, algarismos e '_'.") String codigo,
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Pattern(regexp = "PLANO|PROGRAMA|SRUP", message = "Tem de ser PLANO, PROGRAMA ou SRUP.") String categoria,
        @NotNull Boolean ativo) {}
