package com.conkord.saigt.dto.instrumento;

import java.util.List;

/** Os campos do formulário `REN` que o detalhe do Processo achata; nulo nos outros formulários. */
public record FormularioRenResponse(
        MotivoProcedimentoResponse motivoProcedimento,
        List<MotivoProcedimentoResponse> motivosProcedimentoDisponiveis,
        boolean pedeDiplomaCorrigido,
        String diplomaCorrigido,
        String tipoInformacaoFixo,
        String regiaoCodigo,
        String municipioCodigo
) {
}
