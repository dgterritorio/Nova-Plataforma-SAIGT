package com.conkord.saigt.exception;

public class NotificacaoSemConversacaoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_SEM_CONVERSACAO";

    public NotificacaoSemConversacaoException() {
        super(CODIGO, "Este tipo de notificação não tem conversação.");
    }
}
