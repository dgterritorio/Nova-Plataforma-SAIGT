package com.conkord.saigt.service.login;

import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import java.time.Instant;

/** Resultado dos grants que abrem a sessão ativa: o token de acesso, a sua validade e o refresh token. */
public record LoginConcluido(String accessToken, Instant emitidoEm, Instant expiraEm, OAuth2RefreshToken refreshToken) {
}
