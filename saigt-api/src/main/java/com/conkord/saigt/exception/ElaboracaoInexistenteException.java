package com.conkord.saigt.exception;

/** O tipo de Instrumento não tem Elaboração configurada. Falha → 404; o ecrã segue então o caminho de hoje. */
public class ElaboracaoInexistenteException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "ELABORACAO_INEXISTENTE";

    public ElaboracaoInexistenteException() {
        super(CODIGO, "Este tipo de IGT não tem Elaboração.");
    }
}
