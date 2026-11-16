package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * ZIP de uma peça gráfica comunicado em `deposits/files`, só como link. Sem metadados de folha: o SNIT obtém-nos pelo
 * `consultaPlantas`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FicheiroDepositoSnit(
        String fileId,
        String fileName,
        String downloadName,
        String downloadUrl,
        Boolean isGraphicPiece,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
}
