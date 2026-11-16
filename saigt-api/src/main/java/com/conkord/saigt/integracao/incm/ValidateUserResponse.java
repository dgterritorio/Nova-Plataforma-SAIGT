package com.conkord.saigt.integracao.incm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record ValidateUserResponse(
        @JsonProperty("IsSuccess") boolean isSuccess,
        @JsonProperty("ErrorMessage") String errorMessage,
        @JsonProperty("UserInformation") UserInformationPayload userInformation) {

    record UserInformationPayload(
            @JsonProperty("UserName") String userName,
            @JsonProperty("UserEmailAddress") String userEmailAddress,
            @JsonProperty("UserJobTitle") String userJobTitle,
            @JsonProperty("UserEnrollmentAgreementList") List<AdesaoPayload> enrollmentAgreementList) {
    }

    record AdesaoPayload(
            @JsonProperty("UserEnrollmentAgreementIdentifier") String enrollmentIdentifier,
            @JsonProperty("PublishingEntityName") String entityName,
            @JsonProperty("PublishingEntityTaxId") String entityTaxId,
            @JsonProperty("PublishingEntityAddress") String entityAddress,
            @JsonProperty("PublishingEntityAddressNumber") String entityAddressNumber,
            @JsonProperty("PublishingEntityFloor") String entityFloor,
            @JsonProperty("PublishingEntityPostalCode") String entityPostalCode,
            @JsonProperty("PublishingEntityPostalDesignation") String entityPostalDesignation,
            @JsonProperty("PublishingEntityLocality") String entityLocality,
            @JsonProperty("PublishingEntityPhoneNumber") String entityPhoneNumber,
            @JsonProperty("PublishingEntityEmailAddress") String entityEmailAddress,
            @JsonProperty("PublishingEntityResponsible") ResponsiblePayload responsible,
            @JsonProperty("PublishingEntityDRSeries2PartInformation") PartPayload part,
            @JsonProperty("DiarioRepublicaSeries1AvailableActTypeList") List<ActTypePayload> serie1,
            @JsonProperty("DiarioRepublicaSeries2AvailableActTypeList") List<ActTypePayload> serie2) {
    }

    record ResponsiblePayload(
            @JsonProperty("PublishingEntityResponsibleName") String name,
            @JsonProperty("PublishingEntityResponsibleEmailAddress") String email,
            @JsonProperty("PublishingEntityResponsibleJobTitle") String jobTitle) {
    }

    record PartPayload(
            @JsonProperty("PartId") Long partId,
            @JsonProperty("PartLetter") String partLetter,
            @JsonProperty("PartDescription") String partDescription) {
    }

    record ActTypePayload(
            @JsonProperty("ActTypeId") Long actTypeId,
            @JsonProperty("ActTypeName") String actTypeName,
            @JsonProperty("ActTypeCanPublishExtract") boolean canPublishExtract) {
    }
}
