package com.conkord.saigt.dto.utilizador;

/** Alteração do perfil interno de um utilizador AD_DGT existente. Sem motivo: um `motivo` enviado é ignorado. */
public record AlterarPerfilInternoRequest(String perfilInterno) {
}
