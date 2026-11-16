package com.conkord.saigt.exception;

import java.util.List;

/** Campo do formulário inicial alterado depois da primeira submissão. Falha → 409 com `campos`. */
public class CampoBloqueadoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "CAMPO_BLOQUEADO";
    public static final String MENSAGEM = "Desbloqueie o campo antes de o alterar.";
    public static final String ERRO_CAMPO = "Campo bloqueado depois da primeira submissão.";

    public CampoBloqueadoException(List<ValidacaoException.CampoErro> campos) {
        super(CODIGO, MENSAGEM, campos);
    }
}
