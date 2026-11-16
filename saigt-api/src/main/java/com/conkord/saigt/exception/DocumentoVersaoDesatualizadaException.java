package com.conkord.saigt.exception;

/** Outra escrita alterou o documento durante a operação (`versao` do documento). Falha → 409. */
public class DocumentoVersaoDesatualizadaException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String MENSAGEM = "O documento foi alterado; recarregue.";

    public DocumentoVersaoDesatualizadaException() {
        super("DOCUMENTO_VERSAO_DESATUALIZADA", MENSAGEM);
    }
}
