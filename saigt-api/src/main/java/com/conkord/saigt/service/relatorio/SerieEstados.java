package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.ProcessoRepository.EstadoContagemProjecao;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Um ponto por estado, pela ordem do ciclo de vida, com 0 nos estados sem processos. */
final class SerieEstados {

    private SerieEstados() {
    }

    static List<PontoSerieResponse> deContagens(List<EstadoContagemProjecao> contagens) {
        Map<EstadoProcesso, Long> porEstado = new EnumMap<>(EstadoProcesso.class);
        for (EstadoContagemProjecao contagem : contagens) {
            porEstado.merge(contagem.getEstado(), contagem.getTotal(), Long::sum);
        }
        return Arrays.stream(EstadoProcesso.values())
                .map(estado -> new PontoSerieResponse(estado.name(), porEstado.getOrDefault(estado, 0L)))
                .toList();
    }

    static long total(List<PontoSerieResponse> serie) {
        return serie.stream().mapToLong(PontoSerieResponse::valor).sum();
    }
}
