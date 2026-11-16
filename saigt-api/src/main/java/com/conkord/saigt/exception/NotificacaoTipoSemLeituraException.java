package com.conkord.saigt.exception;

public class NotificacaoTipoSemLeituraException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_SEM_LEITURA";

    public NotificacaoTipoSemLeituraException() {
        super(CODIGO, "Este tipo de notificação não tem leitura.");
    }
}
