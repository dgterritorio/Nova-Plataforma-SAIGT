package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 rejeitado pelo próprio Spring Security (JWT ausente/inválido), no mesmo envelope de erro. O
 * {@link BearerTokenAuthenticationEntryPoint} só escreve o `WWW-Authenticate` da RFC 6750.
 */
@Component
public class EnvelopeAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint cabecalhoBearer = new BearerTokenAuthenticationEntryPoint();

    public EnvelopeAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        cabecalhoBearer.commence(request, response, authException);
        EnvelopeErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                "NAO_AUTENTICADO", "Autenticação necessária.");
    }
}
