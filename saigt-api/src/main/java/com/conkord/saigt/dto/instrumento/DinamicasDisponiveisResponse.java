package com.conkord.saigt.dto.instrumento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** `GET /api/v1/dinamicas/disponivel`: as Dinâmicas iniciadas sem IGT de partida em curso que a sessão acompanha. */
public record DinamicasDisponiveisResponse(List<DinamicaSemIgtEmCurso> dinamicasEmCurso) {

    public record DinamicaSemIgtEmCurso(
            UUID dinamicaId,
            String numeroDinamica,
            String tipoDinamica,
            String nomeDinamica,
            String descritivo,
            String estado,
            Instant dataInicio,
            /** Nula enquanto nenhum Procedimento da Dinâmica estiver publicado. */
            LocalDate dataCaducidade,
            /** Os Instrumentos que a Dinâmica abrange; vazia antes da associação. */
            List<InstrumentoAbrangido> instrumentos,
            /** O último processo `PUBLICADO` da Dinâmica visível à sessão, ou nulo. */
            AtoPublicado atoPublicado,
            List<ProcedimentoDisponivelResponse> procedimentosDisponiveis
    ) {
    }

    /** `concelhos`: códigos DTCC, por `instrumento_concelho`. */
    public record InstrumentoAbrangido(UUID id, String designacao, String tipoInstrumento, List<String> concelhos) {
    }

    public record AtoPublicado(UUID processoId, String numeroDr, LocalDate dataPublicacaoDr) {
    }
}
