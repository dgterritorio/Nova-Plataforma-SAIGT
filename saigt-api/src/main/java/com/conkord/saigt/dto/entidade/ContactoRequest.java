package com.conkord.saigt.dto.entidade;

/** `telefone` opcional, 9 dígitos quando preenchido. `principal` omitido é `false`; o contacto é criado sempre ativo. */
public record ContactoRequest(String email, String descricao, String telefone, Boolean principal) {
}
