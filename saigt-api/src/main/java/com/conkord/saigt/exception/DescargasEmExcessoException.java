package com.conkord.saigt.exception;

/** Limite de descargas em simultâneo atingido. Falha → 429. */
public class DescargasEmExcessoException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public DescargasEmExcessoException() {
        super("DESCARGAS_EM_EXCESSO", "Há demasiadas descargas em curso. Tente novamente dentro de instantes.");
    }

    protected DescargasEmExcessoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
