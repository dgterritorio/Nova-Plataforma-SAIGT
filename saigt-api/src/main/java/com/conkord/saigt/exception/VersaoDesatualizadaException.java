package com.conkord.saigt.exception;

/** `versao` do pedido diverge da do processo, ou outra escrita ganhou a corrida. Falha → 409. */
public class VersaoDesatualizadaException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String MENSAGEM = "O processo foi alterado; recarregue.";

    public VersaoDesatualizadaException() {
        super("VERSAO_DESATUALIZADA", MENSAGEM);
    }
}
