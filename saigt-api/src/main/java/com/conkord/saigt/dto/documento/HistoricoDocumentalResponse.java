package com.conkord.saigt.dto.documento;

import java.util.List;

/** Os processos do mesmo Procedimento na mesma Dinâmica, por `ocorrencia` ascendente. */
public record HistoricoDocumentalResponse(List<ProcessoHistoricoDocumentalResponse> processos) {
}
