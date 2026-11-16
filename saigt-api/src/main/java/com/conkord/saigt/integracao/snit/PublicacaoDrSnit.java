package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/** O formato da data fica no DTO para não depender da configuração do `ObjectMapper` do `RestClient`. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicacaoDrSnit(
        String diploma,
        String series,
        String number,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate publicationDate,
        String hyperlink
) {
}
