package com.conkord.saigt.dto.listavalores;

/** Campo {@code null} não é tocado. Sem `tipo`/`codigo`: são a identidade do item, imutável. */
public record AtualizarListaValoresRequest(String descricao, Integer ordem, Boolean ativo) {
}
