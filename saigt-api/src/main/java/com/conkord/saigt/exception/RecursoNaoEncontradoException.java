package com.conkord.saigt.exception;

/** Fora de visibilidade. Falha → 404, nunca 403 (não revela existência do recurso). */
public class RecursoNaoEncontradoException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public RecursoNaoEncontradoException(String mensagem) {
        super("NAO_ENCONTRADO", mensagem);
    }

    protected RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
