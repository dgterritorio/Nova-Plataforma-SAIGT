package com.conkord.saigt.configuration;

import com.conkord.saigt.controller.core.RequestLoggingFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Resource Server: valida os JWT do Authorization Server ({@link AuthorizationServerConfig}), de utilizadores e de
 * sistemas, pelas chaves do `GET /oauth2/jwks`. O claim `sistema` separa os dois. Ordem das cadeias: servidor de
 * autorização (1), links públicos (2) e esta (3), que apanha o resto.
 */
@Configuration
public class SecurityConfig {

    static final String PREFIXO_INTEGRACOES = "/api/v1/integracoes/";

    @Value("${app.security.auth-disabled:false}")
    private boolean authDisabled;

    @Value("${app.security.cors.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audience}")
    private String audience;

    /**
     * Links públicos da INCM e da PCGT: cadeia própria, sem JWT nem sessão, só `GET` e `HEAD`. O token no caminho é a
     * única autorização, validada pelo serviço; o limite de pedidos é por IP.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain linksPublicosFilterChain(HttpSecurity http,
                                                        EnvelopeAuthenticationEntryPoint authenticationEntryPoint,
                                                        EnvelopeAccessDeniedHandler accessDeniedHandler,
                                                        RateLimiterRegistry rateLimiterRegistry,
                                                        ObjectMapper objectMapper) throws Exception {
        http
                .securityMatcher("/api/v1/publico/**")
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 — sem cookies nem sessão
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("sandbox; default-src 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/publico/*/*").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/publico/*/*").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new RequestLoggingFilter(), AuthorizationFilter.class)
                .addFilterBefore(new LimitePedidosPublicoFilter(rateLimiterRegistry, objectMapper),
                        AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain filterChain(HttpSecurity http, EnvelopeAuthenticationEntryPoint authenticationEntryPoint,
                                            EnvelopeAccessDeniedHandler accessDeniedHandler, JwtDecoder jwtDecoder,
                                            RateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF desligado: API stateless, autenticação só por bearer token (nunca cookie),
                // pelo que o browser não envia credenciais num pedido forjado.
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 — ver comentário acima
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // X-Frame-Options e X-Content-Type-Options já vêm por omissão do Spring Security.
                // HSTS só é escrito quando request.isSecure() (ver forward-headers-strategy).
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        if (authDisabled) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/integracoes/snit/**").denyAll()
                    .anyRequest().permitAll());
        } else {
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/actuator/health", "/api/v1/docs/**", "/api/v1/openapi/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/descargas/*").permitAll()
                            .requestMatchers(HttpMethod.HEAD, "/api/v1/descargas/*").permitAll()
                            .requestMatchers(PREFIXO_INTEGRACOES + "snit/**")
                            .access(sistema(AuthorizationServerConfig.SISTEMA_SNIT))
                            .requestMatchers(PREFIXO_INTEGRACOES + "geoconform/**")
                            .access(sistema(AuthorizationServerConfig.SISTEMA_GEOCONFORM))
                            .requestMatchers(PREFIXO_INTEGRACOES + "**").denyAll()
                            .anyRequest().access(utilizador()))
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.decoder(jwtDecoder))
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler));
        }

        http.addFilterBefore(new RequestLoggingFilter(), org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        // Depois da autenticação JWT, para a chave ser o utilizador; pedidos anónimos caem no IP.
        http.addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /** Interfaces de entrada: só o token emitido para aquele sistema. */
    private static AuthorizationManager<RequestAuthorizationContext> sistema(String nome) {
        return (autenticacao, contexto) -> new AuthorizationDecision(nome.equals(claimSistema(autenticacao.get())));
    }

    /** Endpoints de utilizador: qualquer token autenticado que não seja de um cliente de sistema. */
    private static AuthorizationManager<RequestAuthorizationContext> utilizador() {
        return (autenticacao, contexto) -> {
            Authentication atual = autenticacao.get();
            boolean autenticado = atual != null && atual.isAuthenticated() && !(atual instanceof AnonymousAuthenticationToken);
            return new AuthorizationDecision(autenticado && claimSistema(atual) == null);
        };
    }

    private static String claimSistema(Authentication autenticacao) {
        return autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString(AuthorizationServerConfig.CLAIM_SISTEMA)
                : null;
    }

    /**
     * O decoder de jwk-set-uri só valida assinatura e expiração; iss/aud exigem validadores explícitos. O JWKS só é
     * lido no primeiro token a validar (e quando aparece um `kid` desconhecido), não no arranque: a API pode ler o
     * `/oauth2/jwks` dela própria.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(new JwtTimestampValidator(), new JwtIssuerValidator(issuer), withAudience)));

        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
