package com.conkord.saigt.exception;

/** Camada 3c: tipo de ato não autorizado ao utilizador pela INCM. Falha → 403. */
public class TipoAtoNaoAutorizadoException extends AcaoNaoAutorizadaException {
    private static final long serialVersionUID = 1L;

    public TipoAtoNaoAutorizadoException(String mensagem) {
        super("TIPO_ATO_NAO_AUTORIZADO", mensagem);
    }
}
