package com.conkord.saigt.exception;

import java.util.List;

/**
 * Ato referido de um ato pontual inválido: em falta, num Procedimento que não é `ato_pontual`, ou que não é um processo
 * `PUBLICADO` de Decisão de início ou de Discussão pública da mesma Dinâmica. Não distingue os casos, para não revelar
 * processos de outras Dinâmicas. Falha → 400.
 */
public class ReferenciaInvalidaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "REFERENCIA_INVALIDA";
    public static final String CAMPO = "processoReferidoId";
    public static final String MENSAGEM = "Ato referido inválido.";

    public ReferenciaInvalidaException() {
        super(CODIGO, MENSAGEM, List.of(new CampoErro(CAMPO, MENSAGEM)));
    }
}
