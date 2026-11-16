package com.conkord.saigt.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê os parâmetros obrigatórios de um grant de utilizador: cada um uma só vez e não vazio (`invalid_request`).
 * Devolve {@code null} para outros grants, que ficam para os restantes conversores.
 */
class GrantUtilizadorAuthenticationConverter implements AuthenticationConverter {

    private final AuthorizationGrantType grant;
    private final List<String> obrigatorios;

    GrantUtilizadorAuthenticationConverter(AuthorizationGrantType grant, List<String> obrigatorios) {
        this.grant = grant;
        this.obrigatorios = obrigatorios;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!grant.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        Map<String, Object> parametros = new LinkedHashMap<>();
        for (String nome : obrigatorios) {
            String[] valores = request.getParameterValues(nome);
            if (valores == null || valores.length != 1 || valores[0].isBlank()) {
                throw new ErroGrantException(OAuth2ErrorCodes.INVALID_REQUEST,
                        "Parâmetro em falta, vazio ou repetido: " + nome + ".");
            }
            parametros.put(nome, valores[0]);
        }
        return new GrantUtilizadorAuthenticationToken(grant, SecurityContextHolder.getContext().getAuthentication(),
                parametros, request.getRemoteAddr());
    }
}
