package com.conkord.saigt.exception;

/** Já há um processo em curso na Dinâmica, ou do mesmo Procedimento sem poder repetir. Falha → 409. */
public class ProcessoEmCursoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public ProcessoEmCursoException(String mensagem) {
        super("PROCESSO_EM_CURSO", mensagem);
    }
}
