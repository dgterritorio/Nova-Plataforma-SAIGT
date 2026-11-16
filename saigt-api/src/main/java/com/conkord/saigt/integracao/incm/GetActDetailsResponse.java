package com.conkord.saigt.integracao.incm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GetActDetailsResponse(
        @JsonProperty("IsSuccess") boolean isSuccess,
        @JsonProperty("ErrorMessage") String errorMessage,
        @JsonProperty("ProcessingStatus") String processingStatus,
        @JsonProperty("CreatedOn") Instant createdOn,
        @JsonProperty("UpdatedOn") Instant updatedOn,
        @JsonProperty("ActReferenceIdentifier") String actReferenceIdentifier,
        @JsonProperty("FormURL") String formUrl,
        // Só leitura: o ato inteiro, com possíveis dados pessoais, não vai para o log da integração.
        @JsonProperty(value = "ActJSON", access = JsonProperty.Access.WRITE_ONLY) String actJson,
        @JsonProperty("ActStatus") String actStatus,
        @JsonProperty("ActValidationErrorMessageList") List<String> actValidationErrorMessageList,
        @JsonProperty("ActLogList") List<ActLog> actLogList,
        @JsonProperty("Publication") Publication publication) {

    public record ActLog(
            @JsonProperty("ActStatus") String actStatus,
            @JsonProperty("CreatedOn") Instant createdOn) {
    }

    public record Publication(
            @JsonProperty("ActPDFURL") String actPdfUrl,
            @JsonProperty("ActTitle") String actTitle,
            @JsonProperty("ActURL") String actUrl,
            @JsonProperty("DiarioRepublicaTitle") String diarioRepublicaTitle,
            @JsonProperty("ActPublicationDate") LocalDate actPublicationDate) {
    }
}
