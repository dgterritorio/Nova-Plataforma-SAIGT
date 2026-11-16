package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Só tipo e domínio; os limites de meses e dias não se impõem, o Figma não os define. */
public record InformacaoBaseDeliberacaoRequest(
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoAnos,
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoMeses,
        @PositiveOrZero(message = "O prazo tem de ser um número inteiro não negativo.") @JsonDeserialize(using = PrazoInteiroDeserializer.class) Integer prazoDias,
        Boolean avaliacaoAmbiental,
        LocalDate dataDeliberacao,
        @Size(max = 2000, message = "O texto a publicar excede 2000 caracteres.") String textoPublicar
) {
}
