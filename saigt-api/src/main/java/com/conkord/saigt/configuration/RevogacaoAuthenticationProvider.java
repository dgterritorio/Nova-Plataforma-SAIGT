package com.conkord.saigt.configuration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Revogação (RFC 7009) sobre `sessao`. O provider do SAS invalida também o token de acesso da autorização, que a
 * linha não guarda; aqui a linha é revogada pelo {@link OAuth2AuthorizationService#remove}. Um token desconhecido,
 * expirado ou já revogado responde 200 como um válido (RFC 7009 §2.2).
 */
@Component
class RevogacaoAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService autorizacoes;

    RevogacaoAuthenticationProvider(OAuth2AuthorizationService autorizacoes) {
        this.autorizacoes = autorizacoes;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2TokenRevocationAuthenticationToken pedido = (OAuth2TokenRevocationAuthenticationToken) authentication;
        if (!(pedido.getPrincipal() instanceof OAuth2ClientAuthenticationToken cliente) || !cliente.isAuthenticated()
                || cliente.getRegisteredClient() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        OAuth2TokenType tipo = StringUtils.hasText(pedido.getTokenTypeHint())
                ? new OAuth2TokenType(pedido.getTokenTypeHint()) : null;
        OAuth2Authorization autorizacao = autorizacoes.findByToken(pedido.getToken(), tipo);
        if (autorizacao == null && tipo != null) {
            autorizacao = autorizacoes.findByToken(pedido.getToken(), null);
        }
        if (autorizacao == null) {
            return pedido;
        }
        if (!cliente.getRegisteredClient().getId().equals(autorizacao.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        OAuth2Authorization.Token<OAuth2Token> token = autorizacao.getToken(pedido.getToken());
        autorizacoes.remove(autorizacao);
        return new OAuth2TokenRevocationAuthenticationToken(token.getToken(), cliente);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
