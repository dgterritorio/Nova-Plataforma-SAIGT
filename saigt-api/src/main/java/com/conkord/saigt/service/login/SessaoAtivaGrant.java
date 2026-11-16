package com.conkord.saigt.service.login;

import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.sessao.AutorizacoesSessao;
import com.conkord.saigt.service.sessao.SessaoService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Abertura da sessão ativa comum aos grants de utilizador (`entidade` e `ad-password`): refresh token pelo gerador do
 * servidor de autorização e linha `sessao` `ATIVA` pelas {@link AutorizacoesSessao}, depois de revogar as anteriores.
 * Quem chama abre a transação.
 */
@Component
class SessaoAtivaGrant {

    private final OAuth2AuthorizationService autorizacoes;
    private final SessaoService sessaoService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> geradorTokens;

    SessaoAtivaGrant(OAuth2AuthorizationService autorizacoes, SessaoService sessaoService,
                     OAuth2TokenGenerator<? extends OAuth2Token> geradorTokens) {
        this.autorizacoes = autorizacoes;
        this.sessaoService = sessaoService;
        this.geradorTokens = geradorTokens;
    }

    OAuth2RefreshToken gerarRefreshToken(RegisteredClient cliente, Utilizador utilizador, AuthorizationGrantType grant) {
        Authentication principal = UsernamePasswordAuthenticationToken.authenticated(
                utilizador.getId().toString(), null, List.of());
        OAuth2Token token = geradorTokens.generate(DefaultOAuth2TokenContext.builder()
                .registeredClient(cliente)
                .principal(principal)
                .authorizationGrantType(grant)
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build());
        if (!(token instanceof OAuth2RefreshToken refreshToken)) {
            throw new IllegalStateException("O cliente não recebe refresh tokens.");
        }
        return refreshToken;
    }

    /** {@code contexto} é o JSON do contexto de sessão, ou {@code null} quando não há (DGT). */
    void abrir(RegisteredClient cliente, Utilizador utilizador, AuthorizationGrantType grant, String contexto,
               OAuth2RefreshToken refreshToken) {
        sessaoService.revogarSessoesAtivas(utilizador.getId());
        OAuth2Authorization.Builder autorizacao = OAuth2Authorization.withRegisteredClient(cliente)
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(grant)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.ATIVA)
                .refreshToken(refreshToken);
        if (contexto != null) {
            autorizacao.attribute(AutorizacoesSessao.CONTEXTO_SESSAO, contexto);
        }
        autorizacoes.save(autorizacao.build());
    }
}
