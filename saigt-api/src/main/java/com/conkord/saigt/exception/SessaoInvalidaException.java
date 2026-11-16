package com.conkord.saigt.exception;

/** Refresh token desconhecido, já revogado (reutilizado) ou expirado. Falha → 401. */
public class SessaoInvalidaException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public SessaoInvalidaException(String mensagem) {
        super("SESSAO_INVALIDA", mensagem);
    }
}
