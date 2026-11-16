package com.conkord.saigt.dto.instrumento;

import java.util.List;

/**
 * Grupos do Painel Principal pela ordem do tipo de utilizador: externo, `PUBLICADOS`, `EM_CURSO`,
 * `PARA_CORRECAO`, `EM_VALIDACAO`; perfil interno, `PUBLICADOS` e `EM_VALIDACAO`.
 */
public record PainelProcessosResponse(List<GrupoPainelResponse> grupos) {
}
