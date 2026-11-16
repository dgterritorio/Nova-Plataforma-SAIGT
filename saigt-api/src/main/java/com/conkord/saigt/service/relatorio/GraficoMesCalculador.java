package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/** Processos publicados por mês, pela data de publicação em DR; só os meses com publicações. */
@Component
public class GraficoMesCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;

    public GraficoMesCalculador(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.GRAFICO_MES;
    }

    @Override
    public ValorKpi calcular() {
        return new ValorKpi(null, processoRepository.contarPublicadosPorMes().stream()
                .map(mes -> new PontoSerieResponse(YearMonth.from(mes.getMes()).toString(), mes.getTotal()))
                .toList());
    }
}
