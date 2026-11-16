package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlocoRatificacaoPcgt(
        String titulo,
        String numero,
        SeriePcgt serie,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate data,
        String url,
        Boolean publicado,
        FicheiroReferenciaPcgt ficheiro
) {
}
