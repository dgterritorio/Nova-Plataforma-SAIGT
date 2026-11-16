package com.conkord.saigt.exception;

/** Documento com `bloqueadoEdicao` não se substitui nem se remove. Falha → 409. */
public class DocumentoBloqueadoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public DocumentoBloqueadoException(String mensagem) {
        super("DOC_BLOQUEADO", mensagem);
    }
}
