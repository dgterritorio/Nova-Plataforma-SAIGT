package com.conkord.saigt.configuration;

import com.conkord.saigt.service.login.GrantRecusadoException;
import com.conkord.saigt.service.sessao.RefreshGrantService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/**
 * Substitui o provider do SAS, que rodaria o token sem herdar o fim da sessão nem repor as claims da entidade.
 * Nunca devolve {@code null}: o pedido não pode cair noutro provider.
 */
@Component
class RefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final RefreshGrantService servico;

    RefreshTokenAuthenticationProvider(RefreshGrantService servico) {
        this.servico = servico;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2RefreshTokenAuthenticationToken pedido = (OAuth2RefreshTokenAuthenticationToken) authentication;
        if (!(pedido.getPrincipal() instanceof OAuth2ClientAuthenticationToken cliente) || !cliente.isAuthenticated()
                || cliente.getRegisteredClient() == null) {
            throw new ErroGrantException(OAuth2ErrorCodes.INVALID_CLIENT, null);
        }
        RegisteredClient registado = cliente.getRegisteredClient();
        if (!registado.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new ErroGrantException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, null);
        }
        try {
            RefreshGrantService.RefreshConcluido concluido = servico.rodar(pedido.getRefreshToken(), registado);
            OAuth2AccessToken acesso = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, concluido.accessToken(),
                    concluido.emitidoEm(), concluido.expiraEm());
            return new OAuth2AccessTokenAuthenticationToken(registado, cliente, acesso, concluido.refreshToken());
        } catch (GrantRecusadoException e) {
            throw new ErroGrantException(e.codigo(), e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
