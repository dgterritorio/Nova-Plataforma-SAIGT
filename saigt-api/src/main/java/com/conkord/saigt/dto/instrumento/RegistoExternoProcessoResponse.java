package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.service.instrumento.ResultadoRegistoExterno;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** O `ProcessoResponse` do processo criado, com o número de anexos copiados para o depósito. */
public record RegistoExternoProcessoResponse(
        @JsonUnwrapped ProcessoResponse processo,
        int documentosCopiados
) {
    public static RegistoExternoProcessoResponse de(ResultadoRegistoExterno resultado) {
        return new RegistoExternoProcessoResponse(ProcessoResponse.de(resultado.processo()),
                resultado.documentosCopiados());
    }
}
