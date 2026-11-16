package com.conkord.saigt.exception;

import java.util.List;

/**
 * Dinâmica associada que não está disponível para o processo. Não distingue a inexistente da indisponível, para não
 * revelar Dinâmicas fora da regra. Falha → 400.
 */
public class DinamicaAssociadaInvalidaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "DINAMICA_ASSOCIADA_INVALIDA";
    public static final String CAMPO = "dinamicaAssociadaId";
    public static final String MENSAGEM = "A dinâmica indicada não está disponível para associação.";

    public DinamicaAssociadaInvalidaException() {
        super(CODIGO, MENSAGEM, List.of(new CampoErro(CAMPO, MENSAGEM)));
    }
}
