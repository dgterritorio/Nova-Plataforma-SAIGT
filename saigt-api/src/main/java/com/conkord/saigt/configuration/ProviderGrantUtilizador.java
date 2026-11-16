package com.conkord.saigt.configuration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** Base dos providers dos grants de utilizador: cliente autenticado e grant registado nesse cliente. */
abstract class ProviderGrantUtilizador implements AuthenticationProvider {

    private final AuthorizationGrantType grant;

    protected ProviderGrantUtilizador(AuthorizationGrantType grant) {
        this.grant = grant;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        GrantUtilizadorAuthenticationToken pedido = (GrantUtilizadorAuthenticationToken) authentication;
        if (!grant.equals(pedido.getGrantType())) {
            return null;
        }
        if (!(pedido.getPrincipal() instanceof OAuth2ClientAuthenticationToken cliente) || !cliente.isAuthenticated()
                || cliente.getRegisteredClient() == null) {
            throw new ErroGrantException(OAuth2ErrorCodes.INVALID_CLIENT, null);
        }
        RegisteredClient registado = cliente.getRegisteredClient();
        if (!registado.getAuthorizationGrantTypes().contains(grant)) {
            throw new ErroGrantException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, null);
        }
        return executar(pedido, cliente, registado);
    }

    protected abstract Authentication executar(GrantUtilizadorAuthenticationToken pedido,
                                               OAuth2ClientAuthenticationToken cliente, RegisteredClient registado);

    @Override
    public boolean supports(Class<?> authentication) {
        return GrantUtilizadorAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
