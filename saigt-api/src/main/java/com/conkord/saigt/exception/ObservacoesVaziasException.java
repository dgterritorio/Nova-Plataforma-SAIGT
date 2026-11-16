package com.conkord.saigt.exception;

import java.util.List;

/** Observações de um item só com espaços ou vazias. Falha → 400. */
public class ObservacoesVaziasException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public ObservacoesVaziasException(String mensagem, List<CampoErro> campos) {
        super("DOC_OBSERVACOES_VAZIAS", mensagem, campos);
    }
}
