package com.conkord.saigt.exception;

public class NotificacaoDestinatarioInexistenteException extends RecursoNaoEncontradoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_DESTINATARIO_INEXISTENTE";

    public NotificacaoDestinatarioInexistenteException() {
        super(CODIGO, "Destinatário inexistente.");
    }
}
