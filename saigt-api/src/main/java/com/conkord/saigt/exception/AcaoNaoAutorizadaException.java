package com.conkord.saigt.exception;

/** Perfil, autoria ou autorização de publicação. Falha → 403. */
public class AcaoNaoAutorizadaException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public AcaoNaoAutorizadaException(String mensagem) {
        super("SEM_PERMISSAO", mensagem);
    }

    protected AcaoNaoAutorizadaException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
