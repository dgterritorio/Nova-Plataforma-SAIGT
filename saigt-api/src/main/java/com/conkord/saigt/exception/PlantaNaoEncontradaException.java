package com.conkord.saigt.exception;

public class PlantaNaoEncontradaException extends RecursoNaoEncontradoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "PLANTA_NAO_ENCONTRADA";

    public PlantaNaoEncontradaException() {
        super(CODIGO, "A planta não existe.");
    }
}
