package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;

import java.util.List;

public record ValorKpi(Long valor, List<PontoSerieResponse> serie) {
}
