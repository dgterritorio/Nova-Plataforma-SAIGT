package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resposta 201 da criação. `urlAcesso` é o URL de acesso da entidade ao processo no GeoConform. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessoGeoconform(String idus, String numprocSaigt, String estado, String urlAcesso) {
}
