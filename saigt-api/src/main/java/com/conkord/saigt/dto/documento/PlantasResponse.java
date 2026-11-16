package com.conkord.saigt.dto.documento;

import java.util.List;
import java.util.UUID;

/**
 * Página das Plantas sem GeoConform (EF v1.5 §3.1.2): os ficheiros (ZIP de cada planta, TXT dos nomes, cartograma),
 * as linhas do TXT com o TIFF associado e as opções de cada uma. `errosTxt` diz as linhas que não se leram.
 * `cartogramaPdfDisponivel`: há cartograma e todas as linhas têm TIFF, por isso já se pode gerar o PDF.
 */
public record PlantasResponse(
        boolean exigePlantas,
        List<ItemPlantas> itens,
        List<LinhaPlanta> linhas,
        List<String> errosTxt,
        boolean exigeCartograma,
        boolean cartogramaPdfDisponivel
) {

    public record ItemPlantas(Long documentoConfigId, String codigo, String nome, String descricao, String categoria,
                              boolean obrigatorio, List<String> extensoesAceites, FicheiroPlanta ficheiro) {
    }

    public record FicheiroPlanta(UUID id, String nomeFicheiro) {
    }

    /** `opcoes` são os TIFF do ZIP do mesmo tipo de planta (ou de todos, se nenhum ZIP corresponder ao tipo). */
    public record LinhaPlanta(int ordem, String tipo, String nome, String folha, String nomeTiff, List<String> opcoes) {
    }
}
