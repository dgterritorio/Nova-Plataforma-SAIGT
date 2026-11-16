package com.conkord.saigt.exception;

/** O storage falhou a copiar um anexo para o depósito do processo; nada fica gravado. Falha → 503. */
public class CopiaDepositoIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "STORAGE_INDISPONIVEL";

    public CopiaDepositoIndisponivelException(Throwable causa) {
        super(CODIGO, "Não foi possível copiar os anexos da notificação. Tente novamente.", causa);
    }
}
