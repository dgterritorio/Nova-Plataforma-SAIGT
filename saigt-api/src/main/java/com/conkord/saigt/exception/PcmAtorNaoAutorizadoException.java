package com.conkord.saigt.exception;

/** Ação do fluxo PCM pedida por quem não é o ator previsto. Falha → 403. */
public class PcmAtorNaoAutorizadoException extends AcaoNaoAutorizadaException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "PCM_ATOR_NAO_AUTORIZADO";

    public PcmAtorNaoAutorizadoException(String mensagem) {
        super(CODIGO, mensagem);
    }
}
