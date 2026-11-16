package com.conkord.saigt.exception;

import java.util.List;

/** Campo da ficha MOTU fora dos limites. Falha → 400. */
public class MotuCamposInvalidosException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public MotuCamposInvalidosException(String mensagem, List<CampoErro> campos) {
        super("MOTU_CAMPOS_INVALIDOS", mensagem, campos);
    }
}
