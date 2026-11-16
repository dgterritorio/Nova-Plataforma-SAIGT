package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificacaoSnit(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant verifiedAt,
        String verifiedBy
) {
}
