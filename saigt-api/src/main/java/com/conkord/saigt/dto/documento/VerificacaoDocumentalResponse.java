package com.conkord.saigt.dto.documento;

import com.conkord.saigt.service.documento.ResultadoVerificacao;
import com.conkord.saigt.service.documento.VerificacaoChecklist;

import java.util.UUID;

/** Nas linhas de um TIF, `documentoId` é o do ZIP e `nomeFicheiro` o da entrada TIF. */
public record VerificacaoDocumentalResponse(
        VerificacaoChecklist verificacao,
        UUID documentoId,
        String nomeFicheiro,
        ResultadoVerificacao resultado,
        String detalhe
) {
}
