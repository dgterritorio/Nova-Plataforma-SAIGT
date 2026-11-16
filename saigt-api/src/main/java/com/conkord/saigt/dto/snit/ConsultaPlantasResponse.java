package com.conkord.saigt.dto.snit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** Resposta do `consultaPlantas` (contrato `saigt-para-snit_v1.1.yaml`); as chaves sem valor são omitidas. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultaPlantasResponse(List<ProcessoPlantas> processos) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProcessoPlantas(long saigtId, String numeroDeposito, String idus, List<PlantaSnit> plantas) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlantaSnit(
            String idMotuCdg,
            String nomeRegulamento,
            String numeroFolha,
            @JsonProperty("nomeTIFF") String nomeTiff,
            @JsonProperty("nomeZIP") String nomeZip,
            @JsonProperty("linkJPG_DR") String linkJpgDr,
            Integer emVigor,
            Instant atualizadoEm
    ) {}
}
