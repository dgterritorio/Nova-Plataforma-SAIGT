package com.conkord.saigt.exception;

/** Há documentos ou elementos da Informação Base por validar. Falha → 409. */
public class ItensPorReverException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String MENSAGEM = "Reveja todos os documentos antes de continuar.";

    public ItensPorReverException() {
        super("ITENS_POR_REVER", MENSAGEM);
    }
}
