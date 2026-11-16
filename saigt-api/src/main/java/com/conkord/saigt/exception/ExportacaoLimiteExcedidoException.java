package com.conkord.saigt.exception;

/** O conjunto a exportar tem mais linhas do que o limite por exportação. Falha → 400. */
public class ExportacaoLimiteExcedidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "EXPORTACAO_LIMITE_EXCEDIDO";

    public ExportacaoLimiteExcedidoException(int limite) {
        super(CODIGO, "A exportação excede o limite de " + limite + " linhas. Aplique mais filtros e tente de novo.",
                null);
    }
}
