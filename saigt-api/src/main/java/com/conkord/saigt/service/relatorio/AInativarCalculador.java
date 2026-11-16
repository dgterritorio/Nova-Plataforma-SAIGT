package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.InatividadeProcessoService;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Processos `EM_SUBMISSAO` parados desde o limite do primeiro aviso, o mesmo que o job de inatividade usa. */
@Component
public class AInativarCalculador implements CalculadorKpi {

    private final ProcessoRepository processoRepository;
    private final InatividadeProcessoService inatividadeProcessoService;
    private final Clock relogio;

    public AInativarCalculador(ProcessoRepository processoRepository,
                               InatividadeProcessoService inatividadeProcessoService, Clock relogio) {
        this.processoRepository = processoRepository;
        this.inatividadeProcessoService = inatividadeProcessoService;
        this.relogio = relogio;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.A_INATIVAR;
    }

    @Override
    public ValorKpi calcular() {
        return new ValorKpi(processoRepository.countByEstadoAndAtualizadoEmLessThanEqual(EstadoProcesso.EM_SUBMISSAO,
                inatividadeProcessoService.limiteDaConsulta(relogio.instant())), null);
    }
}
