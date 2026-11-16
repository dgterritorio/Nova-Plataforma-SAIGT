package com.conkord.saigt.configuration;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RateLimiterConfig configApertado = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterConfig configSnit = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(
                Map.of("api", configApertado, "snit", configSnit));
        filter = new RateLimitFilter(registry, objectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveDevolver429ComEnvelopeETraceIdQuandoExcedeLimite() throws Exception {
        MockHttpServletRequest pedido1 = pedidoDe("GET", "/api/v1/recurso", "10.0.0.1");
        MockHttpServletResponse resposta1 = new MockHttpServletResponse();
        FilterChain chain1 = mock(FilterChain.class);
        filter.doFilter(pedido1, resposta1, chain1);
        verify(chain1).doFilter(pedido1, resposta1); // consumiu a única permissão do período

        MockHttpServletRequest pedido2 = pedidoDe("GET", "/api/v1/recurso", "10.0.0.1");
        MockHttpServletResponse resposta2 = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);
        org.slf4j.MDC.put("apiTraceId", "trace-abc");
        try {
            filter.doFilter(pedido2, resposta2, chain2);
        } finally {
            org.slf4j.MDC.remove("apiTraceId");
        }

        verify(chain2, never()).doFilter(pedido2, resposta2);
        assertThat(resposta2.getStatus()).isEqualTo(429);
        ErrorResponse body = objectMapper.readValue(resposta2.getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("LIMITE_REQUISICOES_EXCEDIDO");
        assertThat(body.traceId()).isEqualTo("trace-abc");
    }

    @Test
    void doisUtilizadoresAutenticadosNaoPartilhamQuota() throws Exception {
        String subA = UUID.randomUUID().toString();
        String subB = UUID.randomUUID().toString();

        autenticarComo(subA);
        filter.doFilter(pedidoDe("GET", "/api/v1/recurso", "10.0.0.1"), new MockHttpServletResponse(), mock(FilterChain.class));
        MockHttpServletResponse respostaA2 = new MockHttpServletResponse();
        filter.doFilter(pedidoDe("GET", "/api/v1/recurso", "10.0.0.1"), respostaA2, mock(FilterChain.class));
        assertThat(respostaA2.getStatus()).isEqualTo(429); // A já excedeu

        autenticarComo(subB);
        FilterChain chainB = mock(FilterChain.class);
        MockHttpServletRequest pedidoB = pedidoDe("GET", "/api/v1/recurso", "10.0.0.1");
        MockHttpServletResponse respostaB = new MockHttpServletResponse();
        filter.doFilter(pedidoB, respostaB, chainB);

        verify(chainB, times(1)).doFilter(pedidoB, respostaB); // B continua a ser servido
    }

    @Test
    void endpointsDoSnitUsamOLimiteProprioPorCliente() throws Exception {
        autenticarComo("snit-teste");
        String caminho = "/api/v1/integracoes/snit/depositos/00001";
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest pedido = pedidoDe("GET", caminho, "10.0.0.1");
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(pedido, resposta, chain);
            verify(chain).doFilter(pedido, resposta);
        }

        MockHttpServletResponse excedido = new MockHttpServletResponse();
        filter.doFilter(pedidoDe("GET", caminho, "10.0.0.1"), excedido, mock(FilterChain.class));
        assertThat(excedido.getStatus()).isEqualTo(429);

        MockHttpServletRequest pedidoApi = pedidoDe("GET", "/api/v1/recurso", "10.0.0.1");
        MockHttpServletResponse respostaApi = new MockHttpServletResponse();
        FilterChain chainApi = mock(FilterChain.class);
        filter.doFilter(pedidoApi, respostaApi, chainApi);
        verify(chainApi).doFilter(pedidoApi, respostaApi);
    }

    private void autenticarComo(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, sub)
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private MockHttpServletRequest pedidoDe(String metodo, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, uri);
        request.setRemoteAddr(ip);
        return request;
    }
}
