package com.conkord.saigt.dto.documento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Rascunho: pode faltar o TIFF de algumas linhas; a submissão exige-os todos. `ordem` é a linha do TXT. */
public record GravarPlantasRequest(
        @NotNull @Size(max = 500) List<@Valid AssociacaoPlanta> associacoes
) {

    public record AssociacaoPlanta(@NotNull Integer ordem, @NotBlank @Size(max = 300) String nomeTiff) {
    }
}
