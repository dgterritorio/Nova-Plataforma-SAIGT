package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;

import java.util.List;

public record EstruturaDocumentalResponse(
        List<Separador> separadores,
        List<ProcessoDocumentoListagemResponse> outros,
        List<PendenciaDocumentalResponse> pendencias,
        String observacoesOutros
) {

    public record Separador(
            String separador,
            ElementoInformacaoBase elementoInformacaoBase,
            List<ItemDocumentalResponse> publicacao,
            List<ItemDocumentalResponse> deposito
    ) {
    }
}
