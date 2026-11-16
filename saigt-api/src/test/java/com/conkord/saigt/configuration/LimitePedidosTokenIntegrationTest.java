package com.conkord.saigt.configuration;

import com.conkord.saigt.service.login.LdapAutenticador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Limite "auth" de 2 pedidos por minuto, num contexto próprio, para não esgotar o registo de outras classes. Cada
 * teste usa o seu IP (`X-Forwarded-For`), porque o contador é por IP e partilhado pelo contexto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + LimitePedidosTokenIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + LimitePedidosTokenIntegrationTest.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "app.oauth.clientes.snit.client-id=" + LimitePedidosTokenIntegrationTest.SNIT_ID,
        "app.oauth.clientes.snit.client-secret=" + LimitePedidosTokenIntegrationTest.SNIT_SEGREDO,
        "resilience4j.ratelimiter.configs.auth.limit-for-period=2",
        "resilience4j.ratelimiter.configs.auth.limit-refresh-period=1m"
})
@ActiveProfiles("test")
@Import(AuthorizationServerIntegrationTest.Nio2TomcatConfig.class)
class LimitePedidosTokenIntegrationTest {

    static final int PORTA = 18092;
    static final String SNIT_ID = "snit-teste";
    static final String SNIT_SEGREDO = "segredo-ficticio-snit-0123456789";
    private static final String CLIENT_CREDENTIALS = "grant_type=client_credentials";
    private static final String AD_PASSWORD = "grant_type=urn%3Asaigt%3Aparams%3Aoauth%3Agrant-type%3Aad-password"
            + "&client_id=saigt-ui&username=utilizador.ficticio&password=senha-ficticia";

    @MockBean
    private LdapAutenticador ldap;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void deveContarAsTentativasFalhadasERecusarCom429SemBloquearOJwks() throws Exception {
        String ip = "203.0.113.21";
        assertThat(token(ip, basic(SNIT_ID, "segredo-errado"), CLIENT_CREDENTIALS).statusCode()).isEqualTo(401);
        assertThat(token(ip, basic(SNIT_ID, SNIT_SEGREDO), CLIENT_CREDENTIALS).statusCode()).isEqualTo(200);

        assertLimiteExcedido(token(ip, basic(SNIT_ID, SNIT_SEGREDO), CLIENT_CREDENTIALS));

        // O JWKS conta no limite geral por IP, não no dos tokens: o resource server da API continua a lê-lo.
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> jwks = enviar(HttpRequest.newBuilder(URI.create(base() + "/oauth2/jwks"))
                    .header("X-Forwarded-For", ip).GET().build());
            assertThat(jwks.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(jwks.body()).get("keys")).isNotEmpty();
        }
    }

    @Test
    void osGrantsDeUtilizadorContamNoMesmoLimitePorIpQueOsDosSistemas() throws Exception {
        when(ldap.confirmarAcesso(any(), any())).thenReturn(false);
        String ip = "203.0.113.22";

        HttpResponse<String> recusado = token(ip, null, AD_PASSWORD);
        assertThat(recusado.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(recusado.body()).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(token(ip, basic(SNIT_ID, SNIT_SEGREDO), CLIENT_CREDENTIALS).statusCode()).isEqualTo(200);

        assertLimiteExcedido(token(ip, null, AD_PASSWORD));

        // Outro IP continua a ser servido.
        assertThat(token("203.0.113.23", null, AD_PASSWORD).statusCode()).isEqualTo(400);
    }

    @Test
    void aRevogacaoContaUmaVezPorPedidoNoMesmoLimitePorIpQueOEndpointDeTokens() throws Exception {
        String ip = "203.0.113.24";
        assertThat(revogar(ip).statusCode()).isEqualTo(200);
        assertThat(token(ip, basic(SNIT_ID, SNIT_SEGREDO), CLIENT_CREDENTIALS).statusCode()).isEqualTo(200);

        assertLimiteExcedido(revogar(ip));
        assertLimiteExcedido(token(ip, basic(SNIT_ID, SNIT_SEGREDO), CLIENT_CREDENTIALS));
    }

    private HttpResponse<String> revogar(String ip) {
        return enviar(HttpRequest.newBuilder(URI.create(base() + "/oauth2/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.ofString("client_id=saigt-ui&token=desconhecido"))
                .build());
    }

    private void assertLimiteExcedido(HttpResponse<String> resposta) throws Exception {
        assertThat(resposta.statusCode()).isEqualTo(429);
        JsonNode corpo = objectMapper.readTree(resposta.body());
        assertThat(corpo.get("codigo").asText()).isEqualTo("LIMITE_REQUISICOES_EXCEDIDO");
    }

    private HttpResponse<String> token(String ip, String authorization, String corpo) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (authorization != null) {
            pedido.header("Authorization", authorization);
        }
        return enviar(pedido.build());
    }

    private static String basic(String clientId, String segredo) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + segredo).getBytes(StandardCharsets.UTF_8));
    }

    private static String base() {
        return "http://localhost:" + PORTA;
    }

    private HttpResponse<String> enviar(HttpRequest pedido) {
        try {
            return httpClient.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
