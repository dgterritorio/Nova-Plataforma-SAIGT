package com.conkord.saigt.dto.configuracao;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pelo menos um dos dois `ActTypeId` é obrigatório; verifica-se no serviço, por ser uma regra entre campos. */
public record TipoAtoConfigRequest(
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Só maiúsculas, algarismos e '_'.") String codigo,
        @NotBlank @Size(max = 200) String nome,
        @Min(1) Long incmActTypeIdSerie1,
        @Min(1) Long incmActTypeIdSerie2,
        @NotNull @Min(1) Integer ordem,
        @NotNull Boolean ativo) {}
