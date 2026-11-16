package com.conkord.saigt.exception;

import java.util.List;

/** Rede desconhecida ou que o programa não aceita. Falha → 400. */
public class RedesDefesaInvalidasException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO_REDES_DEFESA = "REDES_DEFESA_INVALIDAS";
    public static final String CAMPO = "redesDefesa";
    public static final String MENSAGEM = "Redes de defesa inválidas para este programa.";

    public RedesDefesaInvalidasException() {
        super(CODIGO_REDES_DEFESA, MENSAGEM, List.of(new CampoErro(CAMPO, MENSAGEM)));
    }
}
