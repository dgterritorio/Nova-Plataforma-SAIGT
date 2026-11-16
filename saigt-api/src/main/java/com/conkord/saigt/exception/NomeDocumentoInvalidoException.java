package com.conkord.saigt.exception;

import java.util.List;

/** Nome do ficheiro fora das regras de nomenclatura. Falha → 400. */
public class NomeDocumentoInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public NomeDocumentoInvalidoException(String mensagem, List<CampoErro> campos) {
        super("DOC_NOME_INVALIDO", mensagem, campos);
    }
}
