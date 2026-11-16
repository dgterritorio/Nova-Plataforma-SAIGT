package com.conkord.saigt.controller.core;

import com.conkord.saigt.service.documento.LinkPublicoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Regista cada pedido inbound com o método, o caminho, o estado e a duração em campos; o `correlationId` vem do MDC.
 * O caminho vai sem a query string, sem o ticket de descarga e sem o token dos links públicos.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String PREFIXO_DESCARGAS = "/api/v1/descargas/";
    private static final String TICKET_MASCARADO = "***";
    private static final String JA_REGISTADO = RequestLoggingFilter.class.getName() + ".JA_REGISTADO";

    /**
     * O filtro corre como filtro do servlet e dentro das cadeias de segurança (para os 401 e 403 também ficarem
     * registados); com o mesmo atributo, cada pedido dá um só evento, escrito pela instância mais exterior.
     */
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return JA_REGISTADO;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duracaoMs = System.currentTimeMillis() - start;
            String caminho = caminho(request);
            log.atInfo()
                    .addKeyValue("metodo", request.getMethod())
                    .addKeyValue("caminho", caminho)
                    .addKeyValue("estadoHttp", response.getStatus())
                    .addKeyValue("duracaoMs", duracaoMs)
                    .log("{} {} -> {} ({} ms)", request.getMethod(), caminho, response.getStatus(), duracaoMs);
        }
    }

    static String caminho(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith(PREFIXO_DESCARGAS)) {
            return PREFIXO_DESCARGAS + TICKET_MASCARADO;
        }
        return LinkPublicoService.mascararTokens(uri);
    }
}
