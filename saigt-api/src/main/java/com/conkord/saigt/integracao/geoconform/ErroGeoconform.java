package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corpo `application/problem+json` de uma resposta de erro, com o `codigo` estável do domínio. O `detail` pode trazer
 * dados do pedido: não vai para o log.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErroGeoconform(String type, String title, Integer status, String codigo, String detail) {
}
