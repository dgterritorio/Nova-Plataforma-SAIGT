package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Corpo de erro da PCGT. A `message` pode trazer dados do pedido: quem a registar trunca-a. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErroPcgt(Integer code, String message) {
}
