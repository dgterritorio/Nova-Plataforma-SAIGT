package com.conkord.saigt.exception;

/** Descargas em simultâneo do cliente do SNIT no limite. Falha → 429, com o código dos outros limites do SNIT. */
public class LimiteDescargasClienteException extends DescargasEmExcessoException {
    private static final long serialVersionUID = 1L;

    public LimiteDescargasClienteException() {
        super("LIMITE_REQUISICOES_EXCEDIDO", "Limite de descargas em simultâneo excedido.");
    }
}
