package com.conkord.saigt.dto.entidade;

import com.conkord.saigt.persistence.core.TipoEntidade;

/** Nulo = sem filtro nesse campo. `nome` é pesquisa parcial na descrição SAIGT, na descrição INCM e no código. */
public record EntidadeFiltro(Boolean ativo, String nome, TipoEntidade tipoEntidade) {
}
