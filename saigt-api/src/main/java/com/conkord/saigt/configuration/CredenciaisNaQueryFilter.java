package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Recusa credenciais e tokens na query string do endpoint de tokens: ficariam nos logs de acesso dos proxies,
 * mesmo com o pedido recusado a seguir. Erro no formato da RFC 6749 §5.2.
 */
public class CredenciaisNaQueryFilter extends OncePerRequestFilter {

    private static final Set<String> PARAMETROS_PROIBIDOS = Set.of("client_secret", "access_token", "username", "password", "pre_auth_token",
            "refresh_token", "token");

    private final ObjectMapper objectMapper;

    public CredenciaisNaQueryFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (temParametroProibido(request.getQueryString())) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", OAuth2ErrorCodes.INVALID_REQUEST,
                    "error_description", "Credenciais e tokens não são aceites na query string.")));
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean temParametroProibido(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return false;
        }
        return Arrays.stream(queryString.split("&"))
                .map(par -> par.split("=", 2)[0])
                .map(CredenciaisNaQueryFilter::descodificar)
                .anyMatch(PARAMETROS_PROIBIDOS::contains);
    }

    private static String descodificar(String nome) {
        try {
            return URLDecoder.decode(nome, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return nome;
        }
    }
}
