package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

/** Resposta do `POST` e do `PUT`. O `token` é só informativo: a SAIGT não o guarda. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessoPcgt(
        String token,
        String codigoProcesso,
        String numeroProcesso,
        @JsonProperty("idSSAIGT") String idSSAIGT,
        String numeroProcessoAntigo,
        String designacao,
        String codigoIdus,
        JsonNode codigoIdusDecomposto,
        JsonNode codigosIdusEtapas,
        String municipioToken,
        String tipoInstrumentoId,
        String tipoProcedimentoId,
        String workflowToken,
        String etapaAtualToken,
        String etapaAtualNome,
        Integer etapaAtualOrdem,
        String estado,
        String tipoAto,
        String numeroAto,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataAto,
        String linkAto,
        String criadoEm,
        String atualizadoEm
) {
}
