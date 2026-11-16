package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do `validar`: a `versao` e os campos opcionais do Resumo Validados (API-PROC-012). */
public record ValidarProcessoRequest(
        @NotNull Long versao,
        @Size(max = ValidarProcessoRequest.MAXIMO_NOTAS) String notasIncm,
        @Size(max = ValidarProcessoRequest.MAXIMO_NOTAS) String notasDgt,
        Boolean regUsoSolo,
        Long alteracaoPeotId
) {
    public static final int MAXIMO_NOTAS = 4000;

    public static ValidarProcessoRequest soVersao(Long versao) {
        return new ValidarProcessoRequest(versao, null, null, null, null);
    }
}
