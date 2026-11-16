package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

/** Média, em dias, da primeira submissão à publicação em DR; nula sem processos publicados que contem. */
@Component
public class TempoMedioInstrucaoCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;

    public TempoMedioInstrucaoCalculador(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.TEMPO_MEDIO_INSTRUCAO;
    }

    @Override
    public ValorKpi calcular() {
        return new ValorKpi(ArredondamentoKpi.unidades(processoRepository.mediaDiasDeInstrucao()), null);
    }
}
