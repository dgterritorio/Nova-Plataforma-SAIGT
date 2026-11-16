package com.conkord.saigt.exception;

import java.util.List;

/** Submissão de um Plano sem elementos fundamentais afetados. Falha → 400. */
public class ElementosAfetadosEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public ElementosAfetadosEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("ELEMENTOS_AFETADOS_EM_FALTA", mensagem, campos);
    }
}
