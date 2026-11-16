package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository.RejeicoesSubmissoesProjecao;
import org.springframework.stereotype.Component;

/**
 * Recusas da DGT sobre submissões, em percentagem; nula sem submissões. Só a taxa global: as quebras por entidade,
 * tipologia, Dinâmica e motivo não cabem numa só série.
 */
@Component
public class TaxaRejeicaoCalculador implements CalculadorKpi {

    private final ProcessoRejeicaoRepository processoRejeicaoRepository;

    public TaxaRejeicaoCalculador(ProcessoRejeicaoRepository processoRejeicaoRepository) {
        this.processoRejeicaoRepository = processoRejeicaoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.TAXA_REJEICAO;
    }

    @Override
    public ValorKpi calcular() {
        RejeicoesSubmissoesProjecao contagem = processoRejeicaoRepository.contarRejeicoesDgtESubmissoes();
        return new ValorKpi(ArredondamentoKpi.percentagem(contagem.getRejeicoes(), contagem.getSubmissoes()), null);
    }
}
