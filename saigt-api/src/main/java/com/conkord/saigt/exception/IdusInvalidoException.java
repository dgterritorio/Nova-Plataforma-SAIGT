package com.conkord.saigt.exception;

/**
 * Um segmento do IDUS não cabe nos seus algarismos. A SAIGT não trunca nem corrige: um IDUS errado enviado ao
 * GeoConform, à PCGT ou ao SNIT é difícil de emendar, por isso a operação falha e a transação desfaz-se. Sem
 * handler próprio: sai como erro interno (500), porque o utilizador não o consegue corrigir.
 */
public class IdusInvalidoException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public IdusInvalidoException(String mensagem) {
        super("IDUS_INVALIDO", mensagem);
    }
}
