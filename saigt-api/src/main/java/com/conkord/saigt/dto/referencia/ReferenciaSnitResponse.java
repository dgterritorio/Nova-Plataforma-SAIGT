package com.conkord.saigt.dto.referencia;

/** Um item de uma lista de referência do SNIT lida da réplica. */
public sealed interface ReferenciaSnitResponse permits NutIiSnitResponse, ConcelhoSnitResponse {

    String codigo();

    String nome();
}
