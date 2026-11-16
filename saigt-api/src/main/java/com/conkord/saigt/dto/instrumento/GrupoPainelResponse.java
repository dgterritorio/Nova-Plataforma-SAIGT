package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.service.instrumento.GrupoProcesso;

import java.util.List;

/**
 * `contagem` é o total do grupo, não o da página; `processos` é a página pedida, por n.º de processo descendente.
 */
public record GrupoPainelResponse(
        GrupoProcesso grupo,
        long contagem,
        List<ProcessoListagemResponse> processos
) {
}
