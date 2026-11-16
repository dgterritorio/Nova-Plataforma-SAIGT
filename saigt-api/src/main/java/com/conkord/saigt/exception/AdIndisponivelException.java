package com.conkord.saigt.exception;

/** O AD da DGT não respondeu ao bind (ligação recusada, timeout, erro do servidor). Falha → 500. */
public class AdIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public AdIndisponivelException(String mensagem) {
        super("INTEGRACAO_INDISPONIVEL", mensagem);
    }
}
