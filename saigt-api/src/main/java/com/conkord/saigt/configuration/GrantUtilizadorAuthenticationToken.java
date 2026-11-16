package com.conkord.saigt.configuration;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;

/** Pedido dos grants de utilizador: só os parâmetros obrigatórios de cada grant e o IP de origem. */
class GrantUtilizadorAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {
    private static final long serialVersionUID = 1L;

    private final transient String ipOrigem;

    GrantUtilizadorAuthenticationToken(AuthorizationGrantType grant, Authentication clientPrincipal,
                                       Map<String, Object> parametros, String ipOrigem) {
        super(grant, clientPrincipal, parametros);
        this.ipOrigem = ipOrigem;
    }

    String ipOrigem() {
        return ipOrigem;
    }

    String parametro(String nome) {
        return (String) getAdditionalParameters().get(nome);
    }
}
