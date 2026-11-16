package com.conkord.saigt.dto.referencia;

/** `codigo` é o DTCC; `nut2` é nulo se a réplica não tem a NUTS II do concelho. */
public record ConcelhoSnitResponse(String codigo, String nome, String nut2) implements ReferenciaSnitResponse {
}
