package com.conkord.saigt.configuration;

import com.conkord.saigt.service.core.JwtKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Porta fixa: o jwk-set-uri tem de ser conhecido antes do arranque do contexto. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18089",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:18089/oauth2/jwks",
        "app.security.auth-disabled=false"
})
@ActiveProfiles("test")
@Import(JwtIntegrationTest.Nio2TomcatConfig.class)
class JwtIntegrationTest {

    private static final String RECURSO_PROTEGIDO = "/api/v1/qualquer-recurso";

    /** NIO2 evita o mecanismo de wakeup por Pipe do conector NIO padrão do Tomcat, que falha neste ambiente sandboxed. */
    @TestConfiguration
    static class Nio2TomcatConfig {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Customizer() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtKeyProvider keyProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    /** O `aud` do token de pré-autenticação só é aceite no endpoint de escolha de entidade. */
    @Test
    void deveRejeitarTokenDePreAutenticacaoNumRecursoProtegidoNormal() {
        String token = emissorTokens.emitirPreAutenticacao(UUID.randomUUID()).getTokenValue();

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveExporApenasChavesPublicasNoJwks() throws Exception {
        ResponseEntity<String> resposta = restTemplate.getForEntity("/oauth2/jwks", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resposta.getBody());
        assertThat(json.get("keys")).isNotEmpty();
        for (JsonNode chave : json.get("keys")) {
            assertThat(chave.has("kid")).isTrue();
            assertThat(chave.has("n")).isTrue();
            assertThat(chave.has("d")).isFalse();
        }
    }

    @Test
    void deveDeixarPassarCamada1QuandoTokenValido() {
        String token = assinarTokenValido(keyProvider.chaveAtual(), Instant.now().plusSeconds(900));

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRejeitarTokenExpirado() {
        String token = assinarTokenValido(keyProvider.chaveAtual(), Instant.now().minusSeconds(60));

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRejeitarTokenAssinadoComChaveDesconhecida() throws Exception {
        RSAKey chaveEstranha = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        String token = assinarTokenValido(chaveEstranha, Instant.now().plusSeconds(900));

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRejeitarIssuerDiferente() {
        String token = assinarToken(keyProvider.chaveAtual(), Instant.now().plusSeconds(900),
                "https://issuer-errado.pt", "saigt-api");

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRejeitarAudienceDiferente() {
        String token = assinarToken(keyProvider.chaveAtual(), Instant.now().plusSeconds(900),
                "https://auth.saigt.pt", "outra-api");

        ResponseEntity<String> resposta = restTemplate.exchange(RECURSO_PROTEGIDO, HttpMethod.GET,
                comAutorizacao(token), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String assinarTokenValido(RSAKey chave, Instant exp) {
        return assinarToken(chave, exp, "https://auth.saigt.pt", "saigt-api");
    }

    private String assinarToken(RSAKey chave, Instant exp, String iss, String aud) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(iss)
                    .audience(aud)
                    .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                    .expirationTime(Date.from(exp))
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID()).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(chave.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpEntity<Void> comAutorizacao(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
