package com.conkord.saigt.configuration;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Erro de um grant de utilizador. `OAuth2Error` só aceita descrições ASCII (RFC 6749 §5.2) e as mensagens da SAIGT
 * levam acentos: a descrição vai à parte e o {@code Cache-Control}/JSON são escritos pelo tratador do endpoint.
 */
public class ErroGrantException extends OAuth2AuthenticationException {
    private static final long serialVersionUID = 1L;

    private final String descricao;

    public ErroGrantException(String codigo, String descricao) {
        super(new OAuth2Error(codigo));
        this.descricao = descricao;
    }

    public String codigo() {
        return getError().getErrorCode();
    }

    public String descricao() {
        return descricao;
    }
}
