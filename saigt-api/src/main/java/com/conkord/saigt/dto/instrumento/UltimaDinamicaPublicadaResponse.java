package com.conkord.saigt.dto.instrumento;

import java.time.Instant;
import java.util.UUID;

/** Última Dinâmica `TERMINADA` que abrange o Instrumento, por `dataConclusao`. */
public record UltimaDinamicaPublicadaResponse(UUID dinamicaId, String tipoDinamica, String nomeDinamica, Instant dataConclusao) {
}
