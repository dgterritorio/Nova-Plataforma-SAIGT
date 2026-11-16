package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * Corpo do `PATCH /api/geoconform/v1/processos/{idus}/visibilidade`. Os nulos não vão no JSON: o contrato só exemplifica
 * o `motivo` e a `data` ao retirar o pedido.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoVisibilidadeGeoconform(
        boolean visivel,
        String motivo,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate data
) {
}
