package com.conkord.saigt.exception;

/** Numa Dinâmica com GeoConform as peças gráficas não se carregam nem se substituem na SAIGT. Falha → 409. */
public class CanalGeoconformException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public CanalGeoconformException(String mensagem) {
        super("DOC_CANAL_GEOCONFORM", mensagem);
    }
}
