package com.conkord.saigt.exception;

import java.util.List;

/** Instrumento que não está em vigor, de tipo que a Dinâmica não admite, ou repetido no conjunto. Falha → 400. */
public class AssociacaoInvalidaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "ASSOCIACAO_INVALIDA";

    public AssociacaoInvalidaException(String mensagem, List<CampoErro> campos) {
        super(CODIGO, mensagem, campos);
    }
}
