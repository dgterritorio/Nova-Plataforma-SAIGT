package com.conkord.saigt.dto.entidade;

/**
 * Campo ausente ou `null` mantém o valor gravado. `telefone` e `descricao` vazios limpam-nos; `email` vazio é inválido.
 */
public record AtualizarContactoRequest(String email, String descricao, String telefone, Boolean principal,
                                       Boolean ativo) {

    public boolean semCampos() {
        return email == null && descricao == null && telefone == null && principal == null && ativo == null;
    }
}
