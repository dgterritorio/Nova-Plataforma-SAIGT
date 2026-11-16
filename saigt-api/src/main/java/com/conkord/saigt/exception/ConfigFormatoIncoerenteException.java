package com.conkord.saigt.exception;

import java.util.List;

public class ConfigFormatoIncoerenteException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_CONFIG_FORMATO = "CONFIG_FORMATO_INCOERENTE";
    public static final String MENSAGEM = "Formato incoerente com a finalidade do documento.";

    public ConfigFormatoIncoerenteException() {
        super(CODIGO_CONFIG_FORMATO, MENSAGEM, List.of(new CampoErro("formato", MENSAGEM)));
    }
}
