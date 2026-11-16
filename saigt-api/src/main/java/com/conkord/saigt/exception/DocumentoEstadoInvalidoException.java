package com.conkord.saigt.exception;

/** Estado do processo não permite escrever documentos. Falha → 409. */
public class DocumentoEstadoInvalidoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public DocumentoEstadoInvalidoException(String mensagem) {
        super("DOC_ESTADO_INVALIDO", mensagem);
    }
}
