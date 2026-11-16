package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Chave de contagem: `sub` do JWT quando autenticado, IP quando anónimo. O `/oauth2/token`, o `/oauth2/revoke` e o
 * `/oauth2/jwks` não passam aqui: têm o seu limite por IP na cadeia do servidor de autorização.
 * Corre depois da autenticação JWT ({@link SecurityConfig}), para o principal já estar resolvido.
 * Configs nomeadas "api"/"snit": "snit" é o limite por cliente do SNIT (a chave é o `sub` do seu token).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CATEGORIA_API = "api";
    private static final String CATEGORIA_SNIT = "snit";
    private static final String PREFIXO_LINKS_PUBLICOS = "/api/v1/publico/";
    private static final String PREFIXO_SNIT = "/api/v1/integracoes/snit/";

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Os links públicos têm o seu próprio limite, por IP, na cadeia pública.
        return isOptionsRequest(request) || request.getRequestURI().startsWith("/actuator/")
                || request.getRequestURI().startsWith(PREFIXO_LINKS_PUBLICOS);
    }

    private boolean isOptionsRequest(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String categoria = categoriaDe(request.getRequestURI());
        String chave = resolverChave(request);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(categoria + ":" + chave, categoria);

        if (rateLimiter.acquirePermission()) {
            chain.doFilter(request, response);
        } else {
            EnvelopeErrorWriter.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                    "LIMITE_REQUISICOES_EXCEDIDO", "Limite de pedidos excedido.");
        }
    }

    private static String categoriaDe(String caminho) {
        return caminho.startsWith(PREFIXO_SNIT) ? CATEGORIA_SNIT : CATEGORIA_API;
    }

    private String resolverChave(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return request.getRemoteAddr();
    }
}
