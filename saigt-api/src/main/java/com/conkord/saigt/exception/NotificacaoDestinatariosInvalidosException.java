package com.conkord.saigt.exception;

public class NotificacaoDestinatariosInvalidosException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_DESTINATARIOS_INVALIDOS";

    public NotificacaoDestinatariosInvalidosException(String mensagem) {
        super(CODIGO, mensagem, null);
    }
}
