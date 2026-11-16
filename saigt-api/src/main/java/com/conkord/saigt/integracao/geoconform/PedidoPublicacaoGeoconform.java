package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Corpo do `POST /api/geoconform/v1/processos/{idus}/publicacao` (I-10): os quatro campos são obrigatórios. Não leva o
 * IDUS da Dinâmica, que vai na correção dos identificadores, nem o `numeroDeposito`.
 */
public record PedidoPublicacaoGeoconform(
        String numprocSaigt,
        String numeroAtoIncm,
        String linkPublicacaoIncm,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataPublicacao
) {
}
