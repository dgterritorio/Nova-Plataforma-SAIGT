package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.Pattern;

/**
 * Chaves de `dados_formulario` que o pedido traz em `dadosFormulario` (API-PROC-007, DB-PROC-014).
 * `simultaneoComPmot`: resposta a «Processo em simultâneo com a formação de PMOT» (EF v1.5 l.1599), só nos
 * processos com `aceitaDinamicaAssociada`; omitida = nula.
 */
public record DadosFormularioRequest(
        @Pattern(regexp = "SIM|NAO", message = "Valores aceites: SIM, NAO.") String simultaneoComPmot
) {
}
