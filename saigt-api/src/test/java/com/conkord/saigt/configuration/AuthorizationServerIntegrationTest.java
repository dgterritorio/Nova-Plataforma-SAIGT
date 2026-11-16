package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Só o SNIT configurado: a API arranca sem as variáveis do GeoConform e esse cliente não existe. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + AuthorizationServerIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + AuthorizationServerIntegrationTest.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "resilience4j.ratelimiter.configs.auth.limit-for-period=1000",
        "app.oauth.clientes.snit.client-id=" + AuthorizationServerIntegrationTest.SNIT_ID,
        "app.oauth.clientes.snit.client-secret=" + AuthorizationServerIntegrationTest.SNIT_SEGREDO
})
@ActiveProfiles("test")
@Import(AuthorizationServerIntegrationTest.Nio2TomcatConfig.class)
class AuthorizationServerIntegrationTest {

    static final int PORTA = 18090;
    static final String SNIT_ID = "snit-teste";
    static final String SNIT_SEGREDO = "segredo-ficticio-snit-0123456789";
    private static final String CLIENT_CREDENTIALS = "grant_type=client_credentials";

    /** NIO2 evita o mecanismo de wakeup por Pipe do conector NIO padrão do Tomcat, que falha neste ambiente sandboxed. */
    @TestConfiguration
    static class Nio2TomcatConfig {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Customizer() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private RegisteredClientRepository clientes;

    @Autowired
    private OAuth2TokenGenerator<? extends OAuth2Token> geradorTokens;

    private static final String UI_ID = "saigt-ui";
    private static final String GRANT_INCM = "grant_type=urn:saigt:params:oauth:grant-type:incm-password"
            + "&username=ana@exemplo.pt&password=senha";

    private final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    @Test
    void deveEmitirAccessTokenSemRefreshTokenParaOSnit() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS);

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode corpo = objectMapper.readTree(resposta.body());
        assertThat(corpo.get("access_token").asText()).isNotBlank();
        assertThat(corpo.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expires_in").asLong()).isBetween(890L, 900L);
        assertThat(corpo.has("refresh_token")).isFalse();

        SignedJWT token = SignedJWT.parse(corpo.get("access_token").asText());
        JWTClaimsSet claims = token.getJWTClaimsSet();
        assertThat(token.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(claims.getIssuer()).isEqualTo("https://auth.saigt.pt");
        assertThat(claims.getAudience()).containsExactly("saigt-api");
        assertThat(claims.getStringClaim(AuthorizationServerConfig.CLAIM_SISTEMA)).isEqualTo("snit");
    }

    @Test
    void deveDevolver401InvalidClientComSegredoErrado() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", SNIT_ID, "segredo-errado", CLIENT_CREDENTIALS);

        assertThat(resposta.statusCode()).isEqualTo(401);
        assertThat(resposta.headers().firstValue("WWW-Authenticate")).hasValue("Basic realm=\"saigt\"");
        JsonNode corpo = objectMapper.readTree(resposta.body());
        assertThat(corpo.get("error").asText()).isEqualTo("invalid_client");
        assertThat(corpo.size()).isEqualTo(1);
    }

    @Test
    void deveDevolver401InvalidClientSemEnvelopeQuandoSemCredenciais() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null, CLIENT_CREDENTIALS);

        assertThat(resposta.statusCode()).isEqualTo(401);
        assertThat(resposta.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                valor -> assertThat(valor).startsWith("Basic"));
        JsonNode corpo = objectMapper.readTree(resposta.body());
        assertThat(corpo.get("error").asText()).isEqualTo("invalid_client");
        assertThat(corpo.has("codigo")).isFalse();
    }

    @Test
    void deveDevolver401InvalidClientNoGrantIncmSemClientIdOuComOClientIdDoSnit() throws Exception {
        for (String corpo : List.of(GRANT_INCM, GRANT_INCM + "&client_id=" + SNIT_ID, GRANT_INCM + "&client_id=desconhecido")) {
            HttpResponse<String> resposta = http.pedirToken("", null, null, corpo);

            assertThat(resposta.statusCode()).as(corpo).isEqualTo(401);
            assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).as(corpo).isEqualTo("invalid_client");
        }
    }

    @Test
    void deveDevolver400UnauthorizedClientQuandoOSaigtUiPedeClientCredentials() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null, CLIENT_CREDENTIALS + "&client_id=" + UI_ID);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("unauthorized_client");
    }

    /** O cliente público passa a autenticação; o que falha a seguir são os campos do grant em falta. */
    @Test
    void deveAutenticarOSaigtUiSemSegredoNosGrantsDeUtilizador() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null,
                "grant_type=urn:saigt:params:oauth:grant-type:incm-password&client_id=" + UI_ID);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("invalid_request");
    }

    @Test
    void deveRecusarOSaigtUiComSegredoOuComHttpBasic() throws Exception {
        assertThat(http.pedirToken("", null, null, GRANT_INCM + "&client_id=" + UI_ID + "&client_secret=qualquer")
                .statusCode()).isEqualTo(401);
        HttpResponse<String> comBasic = http.pedirToken("", UI_ID, "qualquer", GRANT_INCM);
        assertThat(comBasic.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(comBasic.body()).get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    void deveRecusarClientIdRepetido() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null,
                GRANT_INCM + "&client_id=" + UI_ID + "&client_id=" + UI_ID);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("invalid_request");
    }

    @Test
    void deveRecusarComInvalidGrantUmRefreshTokenDesconhecido() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null,
                "grant_type=refresh_token&refresh_token=abc&client_id=" + UI_ID);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void deveRecusarORefreshTokenAosClientesDeSistema() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", SNIT_ID, SNIT_SEGREDO,
                "grant_type=refresh_token&refresh_token=abc");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("unauthorized_client");
    }

    @Test
    void naoDeveCriarLinhasEmSessaoParaOsTokensDeSistema() throws Exception {
        long antes = sessaoRepository.count();

        assertThat(http.pedirToken("", SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS).statusCode()).isEqualTo(200);

        assertThat(sessaoRepository.count()).isEqualTo(antes);
    }

    /** JWT RS256 com `jti`, `sub`, `exp` e audiência própria; o resource server recusa-o como token de acesso. */
    @Test
    void deveEmitirORefreshTokenComoJwtComAudienciaPropriaENuncaComoTokenDeAcesso() throws Exception {
        var ui = clientes.findById("saigt-ui");
        UUID utilizadorId = UUID.randomUUID();
        var contexto = DefaultOAuth2TokenContext.builder()
                .registeredClient(ui)
                .principal(UsernamePasswordAuthenticationToken.authenticated(utilizadorId.toString(), null, List.of()))
                .authorizationGrantType(new AuthorizationGrantType("urn:saigt:params:oauth:grant-type:entidade"))
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build();

        OAuth2RefreshToken refresh = (OAuth2RefreshToken) geradorTokens.generate(contexto);

        assertThat(refresh).isNotNull();
        SignedJWT jwt = SignedJWT.parse(refresh.getTokenValue());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(utilizadorId.toString());
        assertThat(claims.getAudience()).containsExactly("saigt-api-refresh");
        assertThat(claims.getIssuer()).isEqualTo("https://auth.saigt.pt");
        assertThat(java.time.Duration.between(java.time.Instant.now(), claims.getExpirationTime().toInstant()))
                .isCloseTo(java.time.Duration.ofHours(8), java.time.Duration.ofSeconds(5));
        assertThat(http.get("/api/v1/processos", refresh.getTokenValue()).statusCode()).isEqualTo(401);
    }

    @Test
    void naoDeveEmitirRefreshTokenAosClientesDeSistema() {
        var snit = clientes.findByClientId(SNIT_ID);
        var contexto = DefaultOAuth2TokenContext.builder()
                .registeredClient(snit)
                .principal(UsernamePasswordAuthenticationToken.authenticated(SNIT_ID, null, List.of()))
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build();

        assertThat(geradorTokens.generate(contexto)).isNull();
    }

    @Test
    void deveDevolver401InvalidClientParaOGeoConformQuandoNaoConfigurado() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", "geoconform", SNIT_SEGREDO, CLIENT_CREDENTIALS);

        assertThat(resposta.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    void deveDevolver400UnsupportedGrantTypeComGrantPassword() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", SNIT_ID, SNIT_SEGREDO,
                "grant_type=password&username=ana&password=x");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("unsupported_grant_type");
    }

    @Test
    void deveDevolver400InvalidRequestComCredenciaisOuTokenNaQueryString() throws Exception {
        for (String query : List.of("?client_secret=" + SNIT_SEGREDO, "?access_token=abc")) {
            HttpResponse<String> resposta = http.pedirToken(query, SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS);

            assertThat(resposta.statusCode()).as(query).isEqualTo(400);
            assertThat(resposta.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
            assertThat(objectMapper.readTree(resposta.body()).get("error").asText()).isEqualTo("invalid_request");
        }
    }

    @Test
    void devePublicarEmOauth2JwksAChaveDosTokensDeSistemaEDeUtilizador() throws Exception {
        HttpResponse<String> jwksSas = http.get("/oauth2/jwks", null);

        assertThat(jwksSas.statusCode()).isEqualTo(200);
        JsonNode chavesSas = objectMapper.readTree(jwksSas.body()).get("keys");
        assertThat(chavesSas).isNotEmpty();
        List<String> kids = new ArrayList<>();
        chavesSas.forEach(chave -> {
            assertThat(chave.has("d")).isFalse();
            kids.add(chave.get("kid").asText());
        });

        String tokenSas = objectMapper.readTree(http.pedirToken("", SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS).body())
                .get("access_token").asText();
        String tokenUtilizador = emissorTokens.emitirAcesso(new ClaimsUtilizador(UUID.randomUUID(), "Ana Teste",
                "ana.teste@exemplo.pt", "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();
        assertThat(kids).contains(SignedJWT.parse(tokenSas).getHeader().getKeyID(),
                SignedJWT.parse(tokenUtilizador).getHeader().getKeyID());
    }

    /**
     * Fora da cadeia do Authorization Server estes caminhos caem na cadeia da API: a resposta é o envelope
     * NAO_AUTENTICADO, mesmo com credenciais de cliente válidas, e não a resposta OAuth do SAS.
     */
    @Test
    void naoDeveServirOutrosEndpointsDoAuthorizationServer() throws Exception {
        String tokenUtilizador = emissorTokens.emitirAcesso(new ClaimsUtilizador(UUID.randomUUID(), "Ana Teste",
                "ana.teste@exemplo.pt", "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();

        for (String caminho : List.of("/oauth2/authorize?response_type=code&client_id=" + SNIT_ID,
                "/.well-known/oauth-authorization-server")) {
            assertNaoAutenticado(http.get(caminho, null), caminho);
            assertThat(http.get(caminho, tokenUtilizador).statusCode()).as(caminho).isEqualTo(404);
        }

        String sasToken = objectMapper.readTree(http.pedirToken("", SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS).body())
                .get("access_token").asText();
        assertNaoAutenticado(http.postComBasic("/oauth2/introspect", SNIT_ID, SNIT_SEGREDO, "token=" + sasToken),
                "/oauth2/introspect");
    }

    /** Os tokens de sistema não têm linha em `sessao`: a revogação não encontra nada e responde 200 (RFC 7009 §2.2). */
    @Test
    void aRevogacaoDeUmTokenDeSistemaRespondeOkSemEfeito() throws Exception {
        String sasToken = objectMapper.readTree(http.pedirToken("", SNIT_ID, SNIT_SEGREDO, CLIENT_CREDENTIALS).body())
                .get("access_token").asText();

        HttpResponse<String> resposta = http.postComBasic("/oauth2/revoke", SNIT_ID, SNIT_SEGREDO, "token=" + sasToken);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).isEmpty();
    }

    private void assertNaoAutenticado(HttpResponse<String> resposta, String caminho) throws Exception {
        assertThat(resposta.statusCode()).as(caminho).isEqualTo(401);
        assertThat(objectMapper.readTree(resposta.body()).get("codigo").asText()).as(caminho).isEqualTo("NAO_AUTENTICADO");
    }
}
