package com.conkord.saigt.exception;

import java.util.List;

/** Ficheiro sem caixa, caixa diferente da do item, ou tag fora dos itens de depósito obrigatório. Falha → 400. */
public class ClassificacaoInvalidaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_CLASSIFICACAO = "DOC_CLASSIFICACAO_INVALIDA";

    public ClassificacaoInvalidaException(String path, String erro) {
        super(CODIGO_CLASSIFICACAO, "Documento inválido.", List.of(new CampoErro(path, erro)));
    }
}
