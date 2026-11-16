package com.conkord.saigt.integracao.incm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SubmitNewActResponse(
        @JsonProperty("IsSuccess") boolean isSuccess,
        @JsonProperty("ErrorMessageList") List<String> errorMessageList,
        @JsonProperty("ActIdentifier") Long actIdentifier) {
}
