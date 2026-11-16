package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository.TamanhoPorItemProjecao;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ficheiros dos processos já submetidos: `valor` é o total em bytes; cada ponto da série é um item documental com o
 * tamanho médio em bytes. A série só leva um valor por ponto, por isso o total por item não segue.
 */
@Component
public class PadroesTamanhoFicheirosCalculador implements CalculadorKpi {

    private final ProcessoDocumentoRepository processoDocumentoRepository;

    public PadroesTamanhoFicheirosCalculador(ProcessoDocumentoRepository processoDocumentoRepository) {
        this.processoDocumentoRepository = processoDocumentoRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.PADROES_TAMANHO_FICHEIROS;
    }

    @Override
    public ValorKpi calcular() {
        List<TamanhoPorItemProjecao> itens = processoDocumentoRepository.somarTamanhosPorItemDosSubmetidos();
        List<PontoSerieResponse> serie = itens.stream()
                .map(item -> new PontoSerieResponse(item.getRotulo(), ArredondamentoKpi.unidades(item.getMedia())))
                .toList();
        return new ValorKpi(itens.stream().mapToLong(TamanhoPorItemProjecao::getTotal).sum(), serie);
    }
}
