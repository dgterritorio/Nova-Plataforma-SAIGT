package com.conkord.saigt.dto.entidade;

/**
 * Campo ausente ou `null` mantém o valor gravado. `nipc` e `dico` vazios limpam-nos; os outros vazios são inválidos.
 */
public record AtualizarEntidadeRequest(String codigo, String descricaoIncm, String descricaoSaigt, String tipoEntidade,
                                       String nipc, String dico) {

    public boolean semCampos() {
        return codigo == null && descricaoIncm == null && descricaoSaigt == null && tipoEntidade == null
                && nipc == null && dico == null;
    }
}
