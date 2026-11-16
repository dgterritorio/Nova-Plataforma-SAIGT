package com.conkord.saigt.exception;

import java.util.List;

/** «Só depósito» num processo cujo formulário não o admite. Falha → 400. */
public class DestinoInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "DESTINO_INVALIDO";
    public static final String CAMPO = "destino";
    public static final String MENSAGEM = "Este processo não pode ser só para depósito.";

    public DestinoInvalidoException() {
        super(CODIGO, MENSAGEM, List.of(new CampoErro(CAMPO, MENSAGEM)));
    }
}
