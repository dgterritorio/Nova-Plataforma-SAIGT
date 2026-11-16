package com.conkord.saigt.exception;

import java.util.List;

/** O processo não está em condições de seguir no fluxo PCM; o código diz porquê. Falha → 409. */
public class PcmEstadoInvalidoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String ESTADO_INVALIDO = "PCM_ESTADO_INVALIDO";
    public static final String SERIE_NAO_PRIMEIRA = "PCM_SERIE_NAO_PRIMEIRA";
    public static final String JA_ATRIBUIDO = "PCM_JA_ATRIBUIDO";
    public static final String DOCUMENTOS_POR_VALIDAR = "PCM_DOCUMENTOS_POR_VALIDAR";
    public static final String NAO_ATRIBUIDO = "PCM_NAO_ATRIBUIDO";
    public static final String DOCUMENTOS_OBRIGATORIOS_EM_FALTA = "PCM_DOCUMENTOS_OBRIGATORIOS_EM_FALTA";

    public PcmEstadoInvalidoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public PcmEstadoInvalidoException(String codigo, String mensagem, List<ValidacaoException.CampoErro> campos) {
        super(codigo, mensagem, campos);
    }
}
