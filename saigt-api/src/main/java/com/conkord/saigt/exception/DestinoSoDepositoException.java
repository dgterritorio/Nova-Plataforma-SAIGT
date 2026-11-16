package com.conkord.saigt.exception;

/** Envio à INCM ou à PCM de um processo só para depósito. Falha → 409. */
public class DestinoSoDepositoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "DESTINO_SO_DEPOSITO";
    public static final String MENSAGEM = "Este processo é só para depósito e não é enviado à INCM.";

    public DestinoSoDepositoException() {
        super(CODIGO, MENSAGEM);
    }
}
