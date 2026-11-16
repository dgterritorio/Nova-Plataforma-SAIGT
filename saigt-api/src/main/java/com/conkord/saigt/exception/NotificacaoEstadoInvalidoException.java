package com.conkord.saigt.exception;

public class NotificacaoEstadoInvalidoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_ESTADO_INVALIDO";

    public NotificacaoEstadoInvalidoException() {
        this("A notificação já não aceita mensagens ou anexos.");
    }

    public NotificacaoEstadoInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static NotificacaoEstadoInvalidoException jaNaoAtiva() {
        return new NotificacaoEstadoInvalidoException("A notificação já não está ativa.");
    }
}
