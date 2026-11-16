package com.conkord.saigt.service.login;

/** Recusa de um grant de utilizador, com o código OAuth (RFC 6749 §5.2) e a mensagem para o cliente. */
public class GrantRecusadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public static final String INVALID_GRANT = "invalid_grant";
    public static final String SERVER_ERROR = "server_error";

    private final String codigo;

    public GrantRecusadoException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static GrantRecusadoException invalidGrant(String mensagem) {
        return new GrantRecusadoException(INVALID_GRANT, mensagem);
    }
}
