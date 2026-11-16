package com.conkord.saigt.configuration;

import com.conkord.saigt.service.core.TokenService;
import com.conkord.saigt.service.sessao.AutorizacoesSessao;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Refresh token do cliente público como JWT RS256 (`jti`, `sub`, `exp`, `aud` próprio): a base de dados guarda só o
 * `jti`, nunca o token. O gerador por omissão do SAS emite um valor opaco e não o emite a clientes públicos.
 * Só emite a clientes públicos com o grant `refresh_token` registado; a validade é a do cliente, limitada ao fim da
 * sessão numa rotação.
 */
class GeradorRefreshTokenJwt implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final JwtEncoder jwtEncoder;
    private final String emissor;

    GeradorRefreshTokenJwt(JwtEncoder jwtEncoder, String emissor) {
        this.jwtEncoder = jwtEncoder;
        this.emissor = emissor;
    }

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        RegisteredClient cliente = context.getRegisteredClient();
        if (!cliente.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                || !cliente.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            return null;
        }
        Instant emitidoEm = Instant.now();
        Instant expiraEm = emitidoEm.plus(cliente.getTokenSettings().getRefreshTokenTimeToLive());
        Instant fimSessao = context.get(AutorizacoesSessao.FIM_SESSAO);
        if (fimSessao != null && fimSessao.isBefore(expiraEm)) {
            expiraEm = fimSessao;
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emissor)
                .subject(context.getPrincipal().getName())
                .audience(List.of(TokenService.AUDIENCIA_REFRESH_TOKEN))
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .id(UUID.randomUUID().toString())
                .build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
        return new OAuth2RefreshToken(jwt.getTokenValue(), emitidoEm, expiraEm);
    }
}
