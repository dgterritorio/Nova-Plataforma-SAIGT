package com.conkord.saigt.exception;

import java.util.List;

public class ExtensaoNaoAceiteException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_EXTENSAO = "DOC_EXTENSAO_NAO_ACEITE";

    public ExtensaoNaoAceiteException(String mensagem) {
        super(CODIGO_EXTENSAO, mensagem, List.of(new CampoErro("ficheiro", mensagem)));
    }
}
