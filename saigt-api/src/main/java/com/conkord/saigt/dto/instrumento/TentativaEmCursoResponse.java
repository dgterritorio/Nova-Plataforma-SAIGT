package com.conkord.saigt.dto.instrumento;

import java.util.UUID;

/** Só quando a última tentativa do Procedimento está em `EM_SUBMISSAO`: marca-o como retomável em vez de omitido. */
public record TentativaEmCursoResponse(
        UUID processoId,
        int numeroTentativa,
        String estado
) {
}
