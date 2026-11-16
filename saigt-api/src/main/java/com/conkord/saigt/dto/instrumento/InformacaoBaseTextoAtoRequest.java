package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.PositiveOrZero;

/** A data de assinatura do ato não entra aqui: vai na coluna `data_assinatura`, pelo `dataAssinatura` do pedido. */
public record InformacaoBaseTextoAtoRequest(
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoAnos,
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoMeses,
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoDias,
        Boolean avaliacaoAmbiental
) {
}
