package com.conkord.saigt.exception;

public class NotificacaoNaoEncontradaException extends RecursoNaoEncontradoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_NAO_ENCONTRADA";

    public NotificacaoNaoEncontradaException() {
        super(CODIGO, "A notificação não existe ou não está disponível.");
    }
}
