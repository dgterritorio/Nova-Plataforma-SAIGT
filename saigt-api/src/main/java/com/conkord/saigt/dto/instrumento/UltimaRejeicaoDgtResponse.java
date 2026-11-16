package com.conkord.saigt.dto.instrumento;

import java.time.Instant;

/**
 * `motivoDescricao` é a descrição atual de `lista_valores`, nula se o item deixar de existir.
 */
public record UltimaRejeicaoDgtResponse(
        String motivoCodigo,
        String motivoDescricao,
        Instant data,
        String notasGerais
) {
}
