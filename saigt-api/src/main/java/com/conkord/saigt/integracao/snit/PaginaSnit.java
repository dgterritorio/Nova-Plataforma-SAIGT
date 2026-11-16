package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** `page` do SNIT conta a partir de 1. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaSnit(List<IgtSnit> items, long total, int page, int pageSize) {
}
