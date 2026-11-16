package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 rejeitado pelo próprio Spring Security, no mesmo envelope de erro, com o `WWW-Authenticate` da RFC 6750.
 * As interfaces de entrada usam o código do seu contrato; AcaoNaoAutorizadaException, da aplicação, segue pelo
 * GlobalExceptionHandler.
 */
@Component
public class EnvelopeAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final BearerTokenAccessDeniedHandler cabecalhoBearer = new BearerTokenAccessDeniedHandler();

    public EnvelopeAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        cabecalhoBearer.handle(request, response, accessDeniedException);
        String codigo = request.getRequestURI().startsWith(SecurityConfig.PREFIXO_INTEGRACOES)
                ? "ACAO_NAO_AUTORIZADA" : "SEM_PERMISSAO";
        EnvelopeErrorWriter.write(response, objectMapper, HttpStatus.FORBIDDEN, codigo, "Ação não autorizada.");
    }
}
