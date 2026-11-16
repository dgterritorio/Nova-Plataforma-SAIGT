package com.conkord.saigt.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente público: só o `client_id` no corpo, sem segredo, sem PKCE. O SAS só aceita clientes públicos no
 * `authorization_code` com PKCE. Não decide o grant: quem manda é o registo do cliente (só os grants de utilizador e
 * `refresh_token`), por isso `client_credentials` do `saigt-ui` dá `unauthorized_client` e não `invalid_client`.
 * Pedidos com `Authorization` ou `client_secret` ficam para os conversores normais, que os recusam num cliente público.
 */
class ClientePublicoAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) {
            return null;
        }
        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length == 0 || !StringUtils.hasText(clientIds[0])) {
            return null;
        }
        if (clientIds.length > 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Map<String, Object> parametros = new HashMap<>();
        request.getParameterMap().forEach((nome, valores) -> {
            if (valores.length == 1 && !OAuth2ParameterNames.CLIENT_ID.equals(nome)) {
                parametros.put(nome, valores[0]);
            }
        });
        return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, parametros);
    }
}
