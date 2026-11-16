package com.conkord.saigt.exception;

/** O objeto tem retenção e não muda de caixa (BL-DOC-002). Falha → 409. */
public class DocumentoRetidoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public DocumentoRetidoException(String mensagem) {
        super("DOC_RETIDO", mensagem);
    }
}
