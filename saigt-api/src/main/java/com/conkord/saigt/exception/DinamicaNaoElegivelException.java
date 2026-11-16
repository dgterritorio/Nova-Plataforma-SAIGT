package com.conkord.saigt.exception;

/** O motor de regras não disponibiliza a Dinâmica ao Instrumento. Falha → 409. */
public class DinamicaNaoElegivelException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public DinamicaNaoElegivelException(String mensagem) {
        super("DINAMICA_NAO_ELEGIVEL", mensagem);
    }
}
