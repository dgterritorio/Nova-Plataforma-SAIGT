package com.conkord.saigt.configuration;

import com.conkord.saigt.configuration.RepositorioClientesSistema.CredenciaisSistema;
import com.conkord.saigt.controller.core.RequestLoggingFilter;
import com.conkord.saigt.service.core.JwtKeyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Authorization Server embutido, único emissor de tokens: client credentials para os sistemas externos e o
 * cliente público `saigt-ui` para os grants de utilizador ({@link EmissorTokensUtilizador}). Todos são assinados pelo
 * mesmo {@link JwtEncoder}, com a chave atual do {@link JwtKeyProvider} e o mesmo `iss`/`aud`, e o resource server
 * ({@link SecurityConfig}) valida-os todos pelo `GET /oauth2/jwks`.
 */
@Configuration
public class AuthorizationServerConfig {

    static final String CLAIM_SISTEMA = "sistema";
    static final String SISTEMA_SNIT = "snit";
    static final String SISTEMA_GEOCONFORM = "geoconform";

    private static final String ENDPOINT_TOKEN = "/oauth2/token";
    private static final String ENDPOINT_JWKS = "/oauth2/jwks";
    private static final String ENDPOINT_REVOGACAO = "/oauth2/revoke";
    private static final String DESAFIO_BASIC = "Basic realm=\"saigt\"";
    private static final OAuth2ErrorHttpMessageConverter CONVERSOR_ERRO_OAUTH = new OAuth2ErrorHttpMessageConverter();
    /** Tokens e revogação: limite apertado por IP, contra tentativas de adivinhar segredos e palavras-passe. */
    private static final LimitePedidosPublicoFilter.Limite LIMITE_TOKEN =
            new LimitePedidosPublicoFilter.Limite("oauth2-token", "auth");
    /**
     * JWKS: o limite geral da API por IP. O resource server da própria API lê-o daqui; com o limite "auth", tokens com um
     * `kid` desconhecido (que obrigam a reler o JWKS) esgotá-lo-iam e a API passaria a recusar tokens válidos.
     */
    private static final LimitePedidosPublicoFilter.Limite LIMITE_JWKS =
            new LimitePedidosPublicoFilter.Limite("oauth2-jwks", "api");

    /**
     * Só o endpoint de tokens, o de revogação (RFC 7009) e o JWKS: authorize, introspect e metadata ficam fora desta
     * cadeia. O limite por IP conta também as tentativas com segredo errado, por isso corre antes da autenticação do cliente.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                              RateLimiterRegistry rateLimiterRegistry,
                                                              RegisteredClientRepository clientes,
                                                              IncmPasswordAuthenticationProvider incmPassword,
                                                              EntidadeAuthenticationProvider entidade,
                                                              AdPasswordAuthenticationProvider adPassword,
                                                              RefreshTokenAuthenticationProvider refresh,
                                                              RevogacaoAuthenticationProvider revogacaoSessao)
            throws Exception {
        http
                .securityMatcher(ENDPOINT_TOKEN, ENDPOINT_REVOGACAO, ENDPOINT_JWKS)
                .with(new OAuth2AuthorizationServerConfigurer(), authorizationServer -> authorizationServer
                        .clientAuthentication(cliente -> cliente
                                .authenticationConverter(new ClientePublicoAuthenticationConverter())
                                .authenticationProvider(new ClientePublicoAuthenticationProvider(clientes))
                                .errorResponseHandler(falhaAutenticacaoCliente()))
                        .tokenEndpoint(token -> token
                                .accessTokenRequestConverters(conversores -> {
                                    conversores.add(0, new GrantUtilizadorAuthenticationConverter(GrantsUtilizador.ENTIDADE,
                                            List.of(EntidadeAuthenticationProvider.PARAMETRO_PRE_AUTH_TOKEN,
                                                    EntidadeAuthenticationProvider.PARAMETRO_INCM_ENROLLMENT_ID)));
                                    conversores.add(0, new GrantUtilizadorAuthenticationConverter(GrantsUtilizador.AD_PASSWORD,
                                            List.of(AdPasswordAuthenticationProvider.PARAMETRO_USERNAME,
                                                    AdPasswordAuthenticationProvider.PARAMETRO_PASSWORD)));
                                    conversores.add(0, new GrantUtilizadorAuthenticationConverter(GrantsUtilizador.INCM_PASSWORD,
                                            List.of(IncmPasswordAuthenticationProvider.PARAMETRO_USERNAME,
                                                    IncmPasswordAuthenticationProvider.PARAMETRO_PASSWORD)));
                                })
                                .authenticationProviders(providers -> {
                                    providers.removeIf(OAuth2RefreshTokenAuthenticationProvider.class::isInstance);
                                    providers.add(0, refresh);
                                    providers.add(0, entidade);
                                    providers.add(0, incmPassword);
                                    providers.add(0, adPassword);
                                })
                                .accessTokenResponseHandler(sucessoEndpointToken(objectMapper))
                                .errorResponseHandler(falhaEndpointToken(objectMapper)))
                        .tokenRevocationEndpoint(revogacao -> revogacao
                                .authenticationProviders(providers -> {
                                    providers.clear();
                                    providers.add(revogacaoSessao);
                                })
                                .errorResponseHandler(falhaEndpointToken(objectMapper))))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // Sem sessão nem cookies: o cliente autentica-se em cada pedido por HTTP Basic.
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 — ver comentário acima
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(clienteNaoAutenticado()))
                .addFilterBefore(new LimitePedidosPublicoFilter(rateLimiterRegistry, objectMapper, LIMITE_TOKEN,
                        Map.of(ENDPOINT_JWKS, LIMITE_JWKS)), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(new CredenciaisNaQueryFilter(objectMapper), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(new RequestLoggingFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /**
     * O filtro do endpoint de tokens corre depois do AuthorizationFilter: um pedido sem credenciais de cliente é
     * recusado aqui, antes de o SAS o ver. Responde como o SAS (RFC 6749 §5.2), não com o envelope da API.
     */
    private static AuthenticationEntryPoint clienteNaoAutenticado() {
        return (request, response, excecao) -> escreverErroOAuth(response, OAuth2ErrorCodes.INVALID_CLIENT);
    }

    /**
     * Igual ao tratamento por omissão do OAuth2ClientAuthenticationFilter (só o código do erro, 401 para
     * invalid_client), mais o desafio `WWW-Authenticate` que a RFC 6749 §5.2 exige quando o cliente se autenticou
     * pelo cabeçalho Authorization.
     */
    private static AuthenticationFailureHandler falhaAutenticacaoCliente() {
        return (request, response, excecao) -> {
            SecurityContextHolder.clearContext();
            String codigo = excecao instanceof OAuth2AuthenticationException oauth
                    ? oauth.getError().getErrorCode() : OAuth2ErrorCodes.INVALID_CLIENT;
            escreverErroOAuth(response, codigo);
        };
    }

    /**
     * Como o tratamento por omissão do endpoint de tokens (RFC 6749 §5.2; 401 em `invalid_client`), mais `server_error`
     * como 500 e as mensagens com acentos dos grants de utilizador, que o `OAuth2Error` não aceita.
     */
    private static AuthenticationFailureHandler falhaEndpointToken(ObjectMapper objectMapper) {
        return (request, response, excecao) -> {
            SecurityContextHolder.clearContext();
            OAuth2Error erro = excecao instanceof OAuth2AuthenticationException oauth
                    ? oauth.getError() : new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST);
            if (excecao instanceof ErroGrantException grant && grant.descricao() != null) {
                escreverErroComDescricao(response, objectMapper, grant.codigo(), grant.descricao());
            } else {
                escreverErroOAuth(response, erro);
            }
        };
    }

    /**
     * Resposta de sucesso da RFC 6749 §5.1. Escrita aqui para o `expires_in` ser a validade do token (300 e 900) e
     * não os segundos que faltam quando a resposta sai, e para levar o parâmetro `entidades` como lista JSON.
     */
    private static AuthenticationSuccessHandler sucessoEndpointToken(ObjectMapper objectMapper) {
        return (request, response, authentication) -> {
            OAuth2AccessTokenAuthenticationToken resultado = (OAuth2AccessTokenAuthenticationToken) authentication;
            OAuth2AccessToken acesso = resultado.getAccessToken();
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put(OAuth2ParameterNames.ACCESS_TOKEN, acesso.getTokenValue());
            corpo.put(OAuth2ParameterNames.TOKEN_TYPE, acesso.getTokenType().getValue());
            if (acesso.getIssuedAt() != null && acesso.getExpiresAt() != null) {
                corpo.put(OAuth2ParameterNames.EXPIRES_IN, ChronoUnit.SECONDS.between(acesso.getIssuedAt(), acesso.getExpiresAt()));
            }
            if (resultado.getRefreshToken() != null) {
                corpo.put(OAuth2ParameterNames.REFRESH_TOKEN, resultado.getRefreshToken().getTokenValue());
            }
            corpo.putAll(resultado.getAdditionalParameters());
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.getWriter().write(objectMapper.writeValueAsString(corpo));
        };
    }

    private static HttpStatus estadoDoErro(String codigo) {
        if (OAuth2ErrorCodes.INVALID_CLIENT.equals(codigo)) {
            return HttpStatus.UNAUTHORIZED;
        }
        return OAuth2ErrorCodes.SERVER_ERROR.equals(codigo) ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
    }

    private static void escreverErroComDescricao(HttpServletResponse response, ObjectMapper objectMapper, String codigo,
                                                 String descricao) throws IOException {
        response.setStatus(estadoDoErro(codigo).value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", codigo, "error_description", descricao)));
    }

    private static void escreverErroOAuth(HttpServletResponse response, OAuth2Error erro) throws IOException {
        ServletServerHttpResponse resposta = new ServletServerHttpResponse(response);
        HttpStatus estado = estadoDoErro(erro.getErrorCode());
        resposta.setStatusCode(estado);
        if (estado == HttpStatus.UNAUTHORIZED) {
            resposta.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, DESAFIO_BASIC);
        }
        CONVERSOR_ERRO_OAUTH.write(erro, null, resposta);
    }

    private static void escreverErroOAuth(HttpServletResponse response, String codigo) throws IOException {
        ServletServerHttpResponse resposta = new ServletServerHttpResponse(response);
        if (OAuth2ErrorCodes.INVALID_CLIENT.equals(codigo)) {
            resposta.setStatusCode(HttpStatus.UNAUTHORIZED);
            resposta.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, DESAFIO_BASIC);
        } else {
            resposta.setStatusCode(HttpStatus.BAD_REQUEST);
        }
        CONVERSOR_ERRO_OAUTH.write(new OAuth2Error(codigo), null, resposta);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${app.jwt.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .tokenEndpoint(ENDPOINT_TOKEN)
                .jwkSetEndpoint(ENDPOINT_JWKS)
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${app.oauth.ui.client-id:saigt-ui}") String uiClientId,
            @Value("${app.jwt.refresh-token-ttl-hours}") long validadeRefreshHoras,
            @Value("${app.oauth.clientes.snit.client-id:}") String snitClientId,
            @Value("${app.oauth.clientes.snit.client-secret:}") String snitClientSecret,
            @Value("${app.oauth.clientes.geoconform.client-id:}") String geoconformClientId,
            @Value("${app.oauth.clientes.geoconform.client-secret:}") String geoconformClientSecret,
            @Value("${app.jwt.access-token-ttl-minutes}") long validadeMinutos) {
        RepositorioClientesSistema sistemas = new RepositorioClientesSistema(List.of(
                new CredenciaisSistema(SISTEMA_SNIT, "SAIGT_OAUTH_SNIT_CLIENT_ID", snitClientId,
                        "SAIGT_OAUTH_SNIT_CLIENT_SECRET", snitClientSecret),
                new CredenciaisSistema(SISTEMA_GEOCONFORM, "SAIGT_OAUTH_GEOCONFORM_CLIENT_ID", geoconformClientId,
                        "SAIGT_OAUTH_GEOCONFORM_CLIENT_SECRET", geoconformClientSecret)),
                Duration.ofMinutes(validadeMinutos));
        return new RepositorioClientesSaigt(sistemas, uiClientId, Duration.ofMinutes(validadeMinutos),
                Duration.ofHours(validadeRefreshHoras));
    }

    /** Todas as chaves do {@link JwtKeyProvider}, só a parte pública, servidas em /oauth2/jwks. */
    @Bean
    public JWKSource<SecurityContext> jwkSource(JwtKeyProvider keyProvider) {
        return (selector, context) -> selector.select(keyProvider.jwkSet());
    }

    /** Só a chave atual: com várias chaves RS256 o NimbusJwtEncoder não sabe qual escolher. */
    @Bean
    public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        return new NimbusJwtEncoder((selector, context) -> selector.select(new JWKSet(keyProvider.chaveAtual())));
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenClientesSistema(@Value("${app.jwt.audience}") String audience) {
        return context -> {
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                context.getClaims()
                        .audience(List.of(audience))
                        .claim(CLAIM_SISTEMA, context.getRegisteredClient().getId());
            }
        };
    }

    /**
     * JWT para o acesso (com o customizer), valor opaco só se um cliente pedir esse formato, e o refresh token em
     * JWT do cliente público. Sem refresh tokens para os clientes de sistema.
     */
    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JwtEncoder jwtEncoder,
            OAuth2TokenCustomizer<JwtEncodingContext> customizer, @Value("${app.jwt.issuer}") String issuer) {
        JwtGenerator jwt = new JwtGenerator(jwtEncoder);
        jwt.setJwtCustomizer(customizer);
        return new DelegatingOAuth2TokenGenerator(jwt, new OAuth2AccessTokenGenerator(),
                new GeradorRefreshTokenJwt(jwtEncoder, issuer));
    }
}
