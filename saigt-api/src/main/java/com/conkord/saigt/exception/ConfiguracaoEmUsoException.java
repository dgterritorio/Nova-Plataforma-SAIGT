package com.conkord.saigt.exception;

/** Mudança de chave de um registo de configuração que já está referenciado. Falha → 409. */
public class ConfiguracaoEmUsoException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public ConfiguracaoEmUsoException() {
        super("EM_USO", "Este registo já está em uso e a sua chave não pode ser alterada.");
    }
}
