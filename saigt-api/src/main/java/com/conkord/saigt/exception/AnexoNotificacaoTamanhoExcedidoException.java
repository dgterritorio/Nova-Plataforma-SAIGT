package com.conkord.saigt.exception;

import java.util.List;

public class AnexoNotificacaoTamanhoExcedidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_FICHEIRO_EXCEDE_LIMITE";
    private static final String MENSAGEM = "O ficheiro excede o tamanho máximo permitido.";

    public AnexoNotificacaoTamanhoExcedidoException() {
        super(CODIGO, MENSAGEM, List.of(new CampoErro("ficheiro", MENSAGEM)));
    }
}
