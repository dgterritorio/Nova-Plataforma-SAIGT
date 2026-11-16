package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Só o código: a SAIGT nunca cria entidades na PCGT. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntidadePcgt(String codigoEntidade) {
}
