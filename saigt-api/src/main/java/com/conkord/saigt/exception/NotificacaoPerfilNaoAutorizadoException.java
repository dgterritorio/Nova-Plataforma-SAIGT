package com.conkord.saigt.exception;

public class NotificacaoPerfilNaoAutorizadoException extends AcaoNaoAutorizadaException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_PERFIL_NAO_AUTORIZADO";

    public NotificacaoPerfilNaoAutorizadoException(String mensagem) {
        super(CODIGO, mensagem);
    }
}
