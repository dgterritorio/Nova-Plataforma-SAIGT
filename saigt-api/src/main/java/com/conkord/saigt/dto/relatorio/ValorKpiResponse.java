package com.conkord.saigt.dto.relatorio;

import java.time.Instant;
import java.util.List;

/** `valor` nulo nos KPIs que são só gráfico; `serie` nula nos KPIs sem gráfico. */
public record ValorKpiResponse(String codigo, String designacao, Long valor, List<PontoSerieResponse> serie,
                               Instant calculadoEm) {
}
