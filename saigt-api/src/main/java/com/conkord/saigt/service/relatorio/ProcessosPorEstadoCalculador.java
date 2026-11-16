package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessosPorEstadoCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;

    public ProcessosPorEstadoCalculador(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.PROCESSOS_POR_ESTADO;
    }

    @Override
    public ValorKpi calcular() {
        List<PontoSerieResponse> serie = SerieEstados.deContagens(processoRepository.contarPorEstado());
        return new ValorKpi(SerieEstados.total(serie), serie);
    }
}
