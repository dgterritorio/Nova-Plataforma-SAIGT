package com.conkord.saigt.exception;

import java.util.List;

/** Desbloqueio de um ficheiro sem justificação. Falha → 400. */
public class JustificacaoEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public JustificacaoEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("DOC_JUSTIFICACAO_EM_FALTA", mensagem, campos);
    }
}
