package com.conkord.saigt.exception;

import java.util.List;

/** Submissão sem os dados de publicação exigidos pela INCM. Falha → 400. */
public class DadosIncmEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public DadosIncmEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("DADOS_INCM_EM_FALTA", mensagem, campos);
    }
}
