package com.conkord.saigt.dto.snit;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Pedido do `infoEntradaVigor` (contrato SAIGT para SNIT v1.1). O ID do IGT cabe nos 5 algarismos do segmento ID_IGT
 * e a ordem nos 4 da Ordem_Dinâmica definitiva (OI-410).
 */
public record InfoEntradaVigorRequest(
        @NotBlank(message = "Obrigatório.") @Size(max = 50, message = "Máximo 50 caracteres.") String numeroDeposito,
        @Positive(message = "Tem de ser positivo.") Long saigtId,
        @JsonProperty("idIGT") @NotNull(message = "Obrigatório.") @Positive(message = "Tem de ser positivo.")
        @Max(value = 99999, message = "Máximo 5 algarismos.") Long idIGT,
        @NotNull(message = "Obrigatório.") @Positive(message = "Tem de ser positivo.")
        @Max(value = 9999, message = "Máximo 4 algarismos.") Integer ordemDinamica,
        LocalDate dataEntradaVigor
) {
}
