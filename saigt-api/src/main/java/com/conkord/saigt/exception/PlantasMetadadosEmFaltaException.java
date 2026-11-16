package com.conkord.saigt.exception;

import java.util.List;

/** Submissão sem GeoConform com plantas por associar, sem cartograma ou com a ficha de metadados incompleta. Falha → 400. */
public class PlantasMetadadosEmFaltaException extends ValidacaoException {

    private static final long serialVersionUID = 1L;

    public PlantasMetadadosEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("PLANTAS_METADADOS_EM_FALTA", mensagem, campos);
    }
}
