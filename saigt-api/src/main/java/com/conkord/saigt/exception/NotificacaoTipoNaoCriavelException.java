package com.conkord.saigt.exception;

public class NotificacaoTipoNaoCriavelException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_NAO_CRIAVEL";

    public NotificacaoTipoNaoCriavelException() {
        super(CODIGO, "Este tipo de notificação é gerado pela plataforma e não pode ser criado manualmente.", null);
    }
}
