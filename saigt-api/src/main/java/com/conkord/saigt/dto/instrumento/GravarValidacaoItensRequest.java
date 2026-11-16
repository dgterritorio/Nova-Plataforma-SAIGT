package com.conkord.saigt.dto.instrumento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GravarValidacaoItensRequest(
        @NotEmpty List<@NotNull @Valid ItemValidacaoRequest> itens,
        @NotNull Long versao
) {
}
