package com.conkord.saigt.exception;

public class NotificacaoPrazoNaoAplicavelException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_PRAZO_NAO_APLICAVEL";

    public NotificacaoPrazoNaoAplicavelException() {
        super(CODIGO, "Prazo não aplicável a este tipo de notificação.", null);
    }
}
