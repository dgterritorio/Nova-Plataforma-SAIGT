package com.conkord.saigt.exception;

/** Circuit Breaker aberto ou falha de comunicação com a INCM. Falha → 500. */
public class IncmIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public IncmIndisponivelException(String mensagem) {
        super("INTEGRACAO_INDISPONIVEL", mensagem);
    }
}
