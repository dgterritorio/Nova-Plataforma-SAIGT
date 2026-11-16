package com.conkord.saigt.exception;

public abstract class ConkordException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String codigo;

    protected ConkordException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    protected ConkordException(String codigo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}
