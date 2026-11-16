package com.conkord.saigt.dto.instrumento;

import java.util.List;

/** Região e CIM / AM do formulário SGIFR, que o detalhe devolve em campos soltos. */
public record TerritorioSgifrResponse(
        boolean pedeRegiao,
        List<OpcaoListaResponse> opcoesRegiao,
        String regiao,
        String regiaoDescricao,
        boolean pedeCimAm,
        List<OpcaoCimAmResponse> opcoesCimAm,
        String cimAm,
        String cimAmDescricao
) {
    public static final TerritorioSgifrResponse NAO_APLICAVEL =
            new TerritorioSgifrResponse(false, null, null, null, false, null, null, null);
}
