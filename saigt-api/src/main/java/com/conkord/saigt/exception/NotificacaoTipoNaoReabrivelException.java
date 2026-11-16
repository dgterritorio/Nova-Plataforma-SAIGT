package com.conkord.saigt.exception;

public class NotificacaoTipoNaoReabrivelException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_NAO_REABRIVEL";

    public NotificacaoTipoNaoReabrivelException() {
        super(CODIGO, "Só um pedido de apoio pode ser reaberto.");
    }
}
