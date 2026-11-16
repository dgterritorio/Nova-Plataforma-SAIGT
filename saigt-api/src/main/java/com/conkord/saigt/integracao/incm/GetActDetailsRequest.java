package com.conkord.saigt.integracao.incm;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetActDetailsRequest(@JsonProperty("ActIdentifier") long actIdentifier) {
}
