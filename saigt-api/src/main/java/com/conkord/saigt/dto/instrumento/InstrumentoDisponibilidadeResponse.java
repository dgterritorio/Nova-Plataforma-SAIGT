package com.conkord.saigt.dto.instrumento;

import java.util.List;

/** O frontend nunca conhece a estrutura do processo; só renderiza o que esta resposta trouxer. */
public record InstrumentoDisponibilidadeResponse(
        List<DinamicaDisponivelResponse> dinamicasDisponiveis,
        List<DinamicaEmCursoResponse> dinamicasEmCurso,
        /** Dinâmica `TERMINADA` mais recente; `null` se não houver. */
        UltimaDinamicaPublicadaResponse ultimaDinamicaPublicada
) {
}
