package com.conkord.saigt.configuration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Autentica pelo `client_id` só os clientes registados com {@link ClientAuthenticationMethod#NONE}. Um cliente de
 * sistema (com segredo) ou desconhecido dá `invalid_client`, sem distinguir os casos.
 */
class ClientePublicoAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository clientes;

    ClientePublicoAuthenticationProvider(RegisteredClientRepository clientes) {
        this.clientes = clientes;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2ClientAuthenticationToken pedido = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(pedido.getClientAuthenticationMethod())) {
            return null;
        }
        RegisteredClient cliente = clientes.findByClientId(pedido.getPrincipal().toString());
        if (cliente == null || !cliente.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new OAuth2ClientAuthenticationToken(cliente, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
