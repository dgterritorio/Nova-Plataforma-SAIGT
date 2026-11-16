package com.conkord.saigt.exception;

/** Camada 3c: parte da adesão diferente da `serie_dr`. Falha → 403. */
public class ParteDrNaoAutorizadaException extends AcaoNaoAutorizadaException {
    private static final long serialVersionUID = 1L;

    public ParteDrNaoAutorizadaException(String mensagem) {
        super("PARTE_DR_NAO_AUTORIZADA", mensagem);
    }
}
