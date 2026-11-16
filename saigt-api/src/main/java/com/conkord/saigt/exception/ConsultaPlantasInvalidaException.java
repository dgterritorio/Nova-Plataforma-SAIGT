package com.conkord.saigt.exception;

import java.util.List;

/** Pedido inválido do `consultaPlantas`; o código é o do contrato do SNIT. Falha → 400. */
public class ConsultaPlantasInvalidaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_SNIT = "VALIDACAO_FALHOU";

    public ConsultaPlantasInvalidaException(String mensagem, String path, String erro) {
        super(CODIGO_SNIT, mensagem, List.of(new CampoErro(path, erro)));
    }
}
