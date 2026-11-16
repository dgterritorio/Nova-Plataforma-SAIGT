package com.conkord.saigt.dto.configuracao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedeDefesaConfigRequest(
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Só maiúsculas, algarismos e '_'.") String codigo,
        @NotBlank @Size(max = 200) String nome,
        @NotNull Boolean aplicaPra,
        @NotNull Boolean aplicaPsa,
        @NotNull Boolean publicavel) {}
