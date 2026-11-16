package com.conkord.saigt.integracao.incm;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record SubmitNewActRequest(@JsonProperty("DGTAct") DgtAct dgtAct) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DgtAct(
            @JsonProperty("UserEnrollmentAgreementIdentifier") String userEnrollmentAgreementIdentifier,
            @JsonProperty("DiarioRepublicaSeries1Act") DiarioRepublicaSeries1Act diarioRepublicaSeries1Act,
            @JsonProperty("DiarioRepublicaSeries2Act") DiarioRepublicaSeries2Act diarioRepublicaSeries2Act,
            @JsonProperty("ImageURLList") List<String> imageUrlList,
            @JsonProperty("FileURLList") List<String> fileUrlList) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiarioRepublicaSeries1Act(
            @JsonProperty("ActTypeId") Long actTypeId,
            @JsonProperty("PublishInSuplement") boolean publishInSuplement,
            @JsonProperty("SuplementPublishingDate") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate suplementPublishingDate,
            @JsonProperty("ActSummary") String actSummary,
            @JsonProperty("SignatureDate") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate signatureDate) {
    }

    public record DiarioRepublicaSeries2Act(
            @JsonProperty("ActTypeId") Long actTypeId,
            @JsonProperty("PartId") Long partId,
            @JsonProperty("InformationTypeIdentifier") String informationTypeIdentifier,
            @JsonProperty("ActSummary") String actSummary,
            @JsonProperty("SignatureDate") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate signatureDate,
            @JsonProperty("LegalProvision") String legalProvision) {
    }
}
