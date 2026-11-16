package com.conkord.saigt.exception;

/** Procedimento anterior obrigatório sem processo `PUBLICADO`. Falha → 409. */
public class SequenciamentoNaoCumpridoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public SequenciamentoNaoCumpridoException(String mensagem) {
        super("SEQUENCIAMENTO_NAO_CUMPRIDO", mensagem);
    }
}
