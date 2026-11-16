package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Concelho do detalhe do IGT: `dtcc` é o código de 4 algarismos (distrito + concelho). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MunicipioIgtSnit(String dtcc, String name) {
}
