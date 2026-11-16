package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Limite de pedidos por IP nas cadeias sem utilizador: os links públicos (config "publico", 60/min por omissão) e a
 * cadeia do servidor de autorização, com um limite por caminho (ver {@link AuthorizationServerConfig}). Cada limite
 * conta à parte, pelo prefixo da chave. O IP é o que o reverse proxy entrega no `X-Forwarded-For`, que ele próprio
 * substitui. Não é um bean, para não correr fora da cadeia que o cria.
 */
class LimitePedidosPublicoFilter extends OncePerRequestFilter {

    static final String CATEGORIA = "publico";

    /** Chave `prefixoChave:IP`, com a config nomeada do resilience4j. */
    record Limite(String prefixoChave, String config) {
    }

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper objectMapper;
    private final Limite limitePadrao;
    private final Map<String, Limite> excecoesPorCaminho;

    LimitePedidosPublicoFilter(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        this(rateLimiterRegistry, objectMapper, new Limite(CATEGORIA, CATEGORIA), Map.of());
    }

    /** Cada caminho de {@code excecoesPorCaminho} conta no seu limite; todos os outros, no padrão. */
    LimitePedidosPublicoFilter(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper, Limite limitePadrao,
                               Map<String, Limite> excecoesPorCaminho) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.objectMapper = objectMapper;
        this.limitePadrao = limitePadrao;
        this.excecoesPorCaminho = Map.copyOf(excecoesPorCaminho);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limite limite = limiteDe(request);
        if (rateLimiterRegistry.rateLimiter(limite.prefixoChave() + ":" + request.getRemoteAddr(), limite.config())
                .acquirePermission()) {
            chain.doFilter(request, response);
        } else {
            EnvelopeErrorWriter.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                    "LIMITE_REQUISICOES_EXCEDIDO", "Limite de pedidos excedido.");
        }
    }

    private Limite limiteDe(HttpServletRequest request) {
        return excecoesPorCaminho.getOrDefault(request.getRequestURI(), limitePadrao);
    }
}
