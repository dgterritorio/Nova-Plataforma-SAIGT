package com.conkord.saigt.exception;

/** Só as fichas de Conjunto opcionais se removem. Falha → 409. */
public class MotuNaoRemovivelException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public MotuNaoRemovivelException(String mensagem) {
        super("MOTU_NAO_REMOVIVEL", mensagem);
    }
}
