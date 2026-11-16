package com.conkord.saigt.dto.instrumento;

import java.time.Instant;
import java.util.List;

/**
 * Lida da recusa INCM mais recente em `processo_rejeicao` ou, nas recusas anteriores a essa tabela, da entrada
 * `SUBMISSAO_INCM_REJEITADA` ou `REJEICAO_INCM` mais recente da `auditoria`. `mensagens` nunca é nula; só vem
 * vazia numa recusa antiga em que a INCM não devolveu texto.
 */
public record UltimaRejeicaoIncmResponse(
        List<String> mensagens,
        Instant data
) {
}
