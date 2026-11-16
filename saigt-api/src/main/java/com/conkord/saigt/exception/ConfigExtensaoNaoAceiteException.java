package com.conkord.saigt.exception;

import java.util.List;

public class ConfigExtensaoNaoAceiteException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_CONFIG_EXTENSAO = "CONFIG_EXTENSAO_NAO_ACEITE";
    public static final String MENSAGEM = "Extensão não aceite para este tipo de documento.";

    public ConfigExtensaoNaoAceiteException() {
        super(CODIGO_CONFIG_EXTENSAO, MENSAGEM, List.of(new CampoErro("extensoesAceites", MENSAGEM)));
    }
}
