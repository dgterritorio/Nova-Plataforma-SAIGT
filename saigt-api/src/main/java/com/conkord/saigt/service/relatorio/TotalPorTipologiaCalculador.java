package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Total de processos criados por tipo de Instrumento; sem a divisão entre atos prévios e publicados (OI-364). */
@Component
public class TotalPorTipologiaCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;

    public TotalPorTipologiaCalculador(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.TOTAL_POR_TIPOLOGIA;
    }

    @Override
    public ValorKpi calcular() {
        List<PontoSerieResponse> serie = processoRepository.contarPorTipoInstrumento().stream()
                .map(tipo -> new PontoSerieResponse(tipo.getCodigo(), tipo.getTotal()))
                .toList();
        return new ValorKpi(serie.stream().mapToLong(PontoSerieResponse::valor).sum(), serie);
    }
}
