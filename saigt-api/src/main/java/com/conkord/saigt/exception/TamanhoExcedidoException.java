package com.conkord.saigt.exception;

import java.util.List;

/** Ficheiro acima de um limite de tamanho do item ou do Processo, ou item já com o número máximo de ficheiros. Falha → 400. */
public class TamanhoExcedidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_TAMANHO = "DOC_TAMANHO_EXCEDIDO";
    private static final String MENSAGEM = "O ficheiro excede o tamanho máximo permitido.";

    public TamanhoExcedidoException() {
        this(MENSAGEM);
    }

    public TamanhoExcedidoException(String erro) {
        super(CODIGO_TAMANHO, MENSAGEM, List.of(new CampoErro("ficheiro", erro)));
    }
}
