package com.conkord.saigt.exception;

import java.util.List;

/** O destinatário indicado não é um utilizador da PCM. Falha → 400. */
public class PcmUtilizadorInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "PCM_UTILIZADOR_INVALIDO";
    public static final String MENSAGEM = "O utilizador indicado não pertence à PCM.";

    public PcmUtilizadorInvalidoException(String campo) {
        super(CODIGO, MENSAGEM, List.of(new CampoErro(campo, MENSAGEM)));
    }
}
