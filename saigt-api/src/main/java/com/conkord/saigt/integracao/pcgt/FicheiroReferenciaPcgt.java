package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FicheiroReferenciaPcgt(String nome, String url) {
}
