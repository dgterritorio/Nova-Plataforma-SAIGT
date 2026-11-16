package com.conkord.saigt.exception;

/** A peça gráfica já tem ficha de Conjunto. Falha → 409. */
public class MotuPecaComFichaException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public MotuPecaComFichaException(String mensagem) {
        super("MOTU_PECA_COM_FICHA", mensagem);
    }
}
