package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Segurança dos endpoints reais da pasta de depósito, com tokens reais do /oauth2/token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + DepositoSnitSegurancaIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:"
                + DepositoSnitSegurancaIntegrationTest.PORTA + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "app.oauth.clientes.snit.client-id=snit-teste",
        "app.oauth.clientes.snit.client-secret=segredo-ficticio-snit-0123456789",
        "app.oauth.clientes.geoconform.client-id=geoconform-teste",
        "app.oauth.clientes.geoconform.client-secret=segredo-ficticio-geoconform-0123456789"
})
@ActiveProfiles("test")
@Import(IntegracoesSecurityIntegrationTest.Nio2TomcatConfig.class)
class DepositoSnitSegurancaIntegrationTest {

    static final int PORTA = 18093;
    private static final String PASTA = "/api/v1/integracoes/snit/depositos/99999999997";
    private static final String FICHEIRO = PASTA + "/ficheiros/" + UUID.randomUUID();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    private final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    @Test
    void deveDar401SemToken() {
        assertThat(http.get(PASTA, null).statusCode()).isEqualTo(401);
        assertThat(http.get(FICHEIRO, null).statusCode()).isEqualTo(401);
    }

    @Test
    void deveDeixarPassarOTokenDoSnitAteAoEndpoint() throws Exception {
        String token = tokenDe("snit-teste", "segredo-ficticio-snit-0123456789");

        for (String caminho : new String[]{PASTA, FICHEIRO}) {
            HttpResponse<String> resposta = http.get(caminho, token);
            assertThat(resposta.statusCode()).isEqualTo(404);
            assertThat(objectMapper.readTree(resposta.body()).get("codigo").asText())
                    .isEqualTo("PROCESSO_DESCONHECIDO");
        }
    }

    @Test
    void deveRecusarOTokenDoGeoconform() throws Exception {
        String token = tokenDe("geoconform-teste", "segredo-ficticio-geoconform-0123456789");

        assertThat(http.get(PASTA, token).statusCode()).isEqualTo(403);
        assertThat(http.get(FICHEIRO, token).statusCode()).isEqualTo(403);
    }

    @Test
    void deveRecusarOJwtDeUtilizador() {
        String token = emissorTokens.emitirAcesso(new ClaimsUtilizador(UUID.randomUUID(), "Ana Teste",
                "ana.teste@exemplo.pt", "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();

        assertThat(http.get(PASTA, token).statusCode()).isEqualTo(403);
        assertThat(http.get(FICHEIRO, token).statusCode()).isEqualTo(403);
    }

    @Test
    void deveRecusarOTokenDoSnitNumEndpointDeUtilizador() throws Exception {
        String token = tokenDe("snit-teste", "segredo-ficticio-snit-0123456789");

        assertThat(http.get("/api/v1/processos", token).statusCode()).isEqualTo(403);
    }

    private String tokenDe(String clientId, String clientSecret) throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", clientId, clientSecret, "grant_type=client_credentials");
        assertThat(resposta.statusCode()).isEqualTo(200);
        return objectMapper.readTree(resposta.body()).get("access_token").asText();
    }
}
