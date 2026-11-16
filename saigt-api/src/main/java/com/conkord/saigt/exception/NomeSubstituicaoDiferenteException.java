package com.conkord.saigt.exception;

import java.util.List;

/** Planta de substituição com nome diferente do ficheiro recusado. Falha → 400. */
public class NomeSubstituicaoDiferenteException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public NomeSubstituicaoDiferenteException(String mensagem) {
        super("DOC_NOME_SUBSTITUICAO_DIFERENTE", mensagem, List.of(new CampoErro("ficheiro", mensagem)));
    }
}
