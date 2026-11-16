package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A entidade responsável pelo procedimento, que decide quem ativa o processo no GeoConform. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntidadeGeoconform(String designacao, String nifEntidade) {
}
