package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do resource server para as interfaces de entrada, com tokens reais do /oauth2/token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + IntegracoesSecurityIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + IntegracoesSecurityIntegrationTest.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "app.oauth.clientes.snit.client-id=snit-teste",
        "app.oauth.clientes.snit.client-secret=segredo-ficticio-snit-0123456789",
        "app.oauth.clientes.geoconform.client-id=geoconform-teste",
        "app.oauth.clientes.geoconform.client-secret=segredo-ficticio-geoconform-0123456789"
})
@ActiveProfiles("test")
@Import({IntegracoesSecurityIntegrationTest.Nio2TomcatConfig.class,
        IntegracoesSecurityIntegrationTest.ControladorIntegracoesTeste.class})
class IntegracoesSecurityIntegrationTest {

    static final int PORTA = 18091;
    private static final String SNIT = "/api/v1/integracoes/snit/teste";
    private static final String GEOCONFORM = "/api/v1/integracoes/geoconform/teste";
    private static final String PROCESSOS = "/api/v1/processos";

    /** NIO2 evita o mecanismo de wakeup por Pipe do conector NIO padrão do Tomcat, que falha neste ambiente sandboxed. */
    @TestConfiguration
    static class Nio2TomcatConfig {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Customizer() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }

    /** Os endpoints de integração reais ainda não existem; aqui só se testa a regra de segurança. */
    @RestController
    static class ControladorIntegracoesTeste {
        @GetMapping({SNIT, GEOCONFORM})
        Map<String, String> responder() {
            return Map.of("estado", "ok");
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    private final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    @Test
    void deveDeixarOSnitNoSeuCaminhoERecusarNosOutros() throws Exception {
        String token = tokenDe("snit-teste", "segredo-ficticio-snit-0123456789");

        assertThat(http.get(SNIT, token).statusCode()).isEqualTo(200);
        assertProibido(http.get(GEOCONFORM, token), "ACAO_NAO_AUTORIZADA");
        assertProibido(http.get(PROCESSOS, token), "SEM_PERMISSAO");
    }

    @Test
    void deveRecusarOGeoConformNoCaminhoDoSnitComWwwAuthenticate() throws Exception {
        String token = tokenDe("geoconform-teste", "segredo-ficticio-geoconform-0123456789");

        assertThat(http.get(GEOCONFORM, token).statusCode()).isEqualTo(200);
        assertProibido(http.get(SNIT, token), "ACAO_NAO_AUTORIZADA");
    }

    @Test
    void deveDevolver401NaoAutenticadoComWwwAuthenticateSemTokenOuComTokenInvalido() throws Exception {
        for (String token : new String[]{null, "nao-e-um-jwt"}) {
            HttpResponse<String> resposta = http.get(SNIT, token);

            assertThat(resposta.statusCode()).isEqualTo(401);
            assertThat(resposta.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                    valor -> assertThat(valor).startsWith("Bearer"));
            assertThat(resposta.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
            JsonNode corpo = corpo(resposta);
            assertThat(corpo.get("codigo").asText()).isEqualTo("NAO_AUTENTICADO");
            assertThat(corpo.get("mensagem").asText()).isEqualTo("Autenticação necessária.");
        }
    }

    @Test
    void deveAceitarTokenDeUtilizadorComoHojeERecusarNasIntegracoes() throws Exception {
        String token = emissorTokens.emitirAcesso(new ClaimsUtilizador(UUID.randomUUID(), "Ana Teste",
                "ana.teste@exemplo.pt", "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();

        assertThat(http.get(PROCESSOS, token).statusCode()).isNotIn(401, 403);
        assertProibido(http.get(SNIT, token), "ACAO_NAO_AUTORIZADA");
    }

    @Test
    void deveRecusarCaminhosDeIntegracaoSemSistemaAtribuido() throws Exception {
        String token = tokenDe("snit-teste", "segredo-ficticio-snit-0123456789");

        assertProibido(http.get("/api/v1/integracoes/pcgt/teste", token), "ACAO_NAO_AUTORIZADA");
    }

    private void assertProibido(HttpResponse<String> resposta, String codigo) throws Exception {
        assertThat(resposta.statusCode()).isEqualTo(403);
        assertThat(resposta.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                valor -> assertThat(valor).startsWith("Bearer"));
        assertThat(resposta.headers().firstValue("Content-Type")).hasValue("application/json;charset=UTF-8");
        JsonNode corpo = corpo(resposta);
        assertThat(corpo.get("codigo").asText()).isEqualTo(codigo);
        assertThat(corpo.get("mensagem").asText()).isEqualTo("Ação não autorizada.");
        assertThat(corpo.get("timestamp").asText()).isNotBlank();
        assertThat(corpo.get("traceId").asText()).isNotBlank();
    }

    private String tokenDe(String clientId, String clientSecret) throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", clientId, clientSecret, "grant_type=client_credentials");
        assertThat(resposta.statusCode()).isEqualTo(200);
        return corpo(resposta).get("access_token").asText();
    }

    private JsonNode corpo(HttpResponse<String> resposta) throws Exception {
        return objectMapper.readTree(resposta.body());
    }
}
