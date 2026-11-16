package com.conkord.saigt.service.documento;

import java.util.List;

/**
 * O que o GDAL diz de um TIFF. `codigoEpsg` nulo: sem sistema de referência; `compressao` é `NONE` quando o GDAL não
 * indica nenhuma; `unidadeResolucao` segue a etiqueta TIFF (2 = polegada, 3 = centímetro); `georreferenciado` diz que
 * há transformação geográfica, do próprio TIFF ou do TFW que o acompanha no ZIP.
 */
public record CaracteristicasTiff(
        String formato,
        Integer codigoEpsg,
        String compressao,
        List<BandaTiff> bandas,
        Double resolucaoX,
        Double resolucaoY,
        Integer unidadeResolucao,
        boolean georreferenciado) {

    public CaracteristicasTiff {
        bandas = bandas == null ? List.of() : List.copyOf(bandas);
    }

    public record BandaTiff(String tipoDados, String interpretacaoCor) {
    }
}
