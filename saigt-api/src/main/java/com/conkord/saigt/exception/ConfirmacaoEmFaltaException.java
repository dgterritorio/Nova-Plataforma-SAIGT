package com.conkord.saigt.exception;

import java.util.List;

/** Submissão sem as confirmações exigidas pelo formulário do Procedimento. Falha → 400. */
public class ConfirmacaoEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "CONFIRMACAO_EM_FALTA";

    public ConfirmacaoEmFaltaException(String mensagem, List<CampoErro> campos) {
        super(CODIGO, mensagem, campos);
    }
}
