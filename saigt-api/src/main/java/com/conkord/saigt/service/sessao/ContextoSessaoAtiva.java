package com.conkord.saigt.service.sessao;

import com.conkord.saigt.service.core.ClaimsUtilizador;

/** Entidade escolhida no login INCM, para a rotação de refresh não voltar a chamar a INCM. Vazio na DGT. */
public record ContextoSessaoAtiva(ClaimsUtilizador.ClaimsEntidade entidade) {
}
