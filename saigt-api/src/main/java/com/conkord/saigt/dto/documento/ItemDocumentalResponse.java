package com.conkord.saigt.dto.documento;

import java.util.List;

/** Item de `documento_config` com os ficheiros que já o preenchem, do mais antigo para o mais recente. */
public record ItemDocumentalResponse(
        Long documentoConfigId,
        String codigo,
        String nome,
        String categoria,
        String tipoElemento,
        String formato,
        boolean obrigatorio,
        List<String> extensoesAceites,
        List<ProcessoDocumentoListagemResponse> ficheiros,
        String observacoes,
        boolean pendente,
        boolean obtidoDoGeoconform
) {
}
