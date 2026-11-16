package com.conkord.saigt.exception;

import java.util.List;

public class FiltroNotificacaoInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_FILTRO_INVALIDO";

    public FiltroNotificacaoInvalidoException(List<CampoErro> campos) {
        super(CODIGO, "Filtro de notificações inválido.", campos);
    }
}
