package com.conkord.saigt.exception;

public class NotificacaoTipoNaoRemovivelException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_NAO_REMOVIVEL";

    public NotificacaoTipoNaoRemovivelException() {
        super(CODIGO, "Este tipo de notificação não pode ser removido.");
    }
}
