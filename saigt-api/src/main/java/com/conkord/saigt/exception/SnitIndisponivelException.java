package com.conkord.saigt.exception;

/** Circuit Breaker aberto, SNIT por configurar ou falha de comunicação com o SNIT. Falha → 500. */
public class SnitIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public SnitIndisponivelException(String mensagem) {
        super("INTEGRACAO_INDISPONIVEL", mensagem);
    }
}
