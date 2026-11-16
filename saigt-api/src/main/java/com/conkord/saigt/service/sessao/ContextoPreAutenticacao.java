package com.conkord.saigt.service.sessao;

import com.conkord.saigt.service.core.ClaimsUtilizador;

import java.util.List;

/**
 * `sessao.contexto_sessao` de PRE_AUTENTICACAO: todas as adesões INCM até à escolha de entidade, na forma do
 * token final. Nunca vai no token de pré-autenticação.
 */
public record ContextoPreAutenticacao(List<ClaimsUtilizador.ClaimsEntidade> entidades) {
}
