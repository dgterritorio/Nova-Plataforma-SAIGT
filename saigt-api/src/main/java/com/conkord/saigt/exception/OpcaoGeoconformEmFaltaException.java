package com.conkord.saigt.exception;

import java.util.List;

/** Submissão de um Procedimento que pergunta pelo GeoConform sem a resposta. Falha → 400. */
public class OpcaoGeoconformEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public OpcaoGeoconformEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("OPCAO_GEOCONFORM_EM_FALTA", mensagem, campos);
    }
}
