package com.conkord.saigt.exception;

/** Falha de comunicação com o storage de documentos. Falha → 500. */
public class StorageIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public StorageIndisponivelException(String mensagem, Throwable causa) {
        super("INTEGRACAO_INDISPONIVEL", mensagem, causa);
    }
}
