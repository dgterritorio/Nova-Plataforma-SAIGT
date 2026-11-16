package com.conkord.saigt.exception;

import java.util.List;

public class AnexoNotificacaoExtensaoNaoAceiteException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_EXTENSAO_NAO_ACEITE";
    private static final String MENSAGEM = "Extensão não aceite.";

    public AnexoNotificacaoExtensaoNaoAceiteException() {
        super(CODIGO, MENSAGEM, List.of(new CampoErro("ficheiro", MENSAGEM)));
    }
}
