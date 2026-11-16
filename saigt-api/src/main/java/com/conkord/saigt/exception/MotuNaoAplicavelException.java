package com.conkord.saigt.exception;

/** A ficha MOTU não se aplica ao Procedimento do processo. Falha → 409. */
public class MotuNaoAplicavelException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public MotuNaoAplicavelException(String mensagem) {
        super("MOTU_NAO_APLICAVEL", mensagem);
    }
}
