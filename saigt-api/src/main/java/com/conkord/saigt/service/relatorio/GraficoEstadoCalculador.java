package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

/** Número de pedidos em cada estado no momento: só o gráfico, sem valor. */
@Component
public class GraficoEstadoCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;

    public GraficoEstadoCalculador(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.GRAFICO_ESTADO;
    }

    @Override
    public ValorKpi calcular() {
        return new ValorKpi(null, SerieEstados.deContagens(processoRepository.contarPorEstado()));
    }
}
