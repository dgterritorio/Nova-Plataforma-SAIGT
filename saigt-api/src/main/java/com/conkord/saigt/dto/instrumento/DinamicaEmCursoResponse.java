package com.conkord.saigt.dto.instrumento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dinâmica em curso, com os Procedimentos disponíveis agora. */
public record DinamicaEmCursoResponse(
        UUID dinamicaId,
        String tipoDinamica,
        String nomeDinamica,
        String descritivo,
        String estado,
        /** "Iniciada em" no passo 1 de Criar Processo — identifica a Dinâmica quando há várias do mesmo tipo em aberto. */
        Instant dataInicio,
        /** Nula enquanto nenhum Procedimento da Dinâmica estiver publicado. */
        LocalDate dataCaducidade,
        List<ProcedimentoDisponivelResponse> procedimentosDisponiveis
) {
}
