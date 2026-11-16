package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LogJson.Extensao.class)
class TokenGeoconformTest {

    private static final String CLIENTE = "cliente-gc-teste";
    private static final String SEGREDO = "segredo-gc-teste-81fa";
    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.token-gc-1.assinatura";
    private static final String TOKEN_NOVO = "eyJhbGciOiJSUzI1NiJ9.token-gc-2.assinatura";
    private static final String CAMINHO_TOKEN = "/oauth/token";
    private static final ReferenciaChamada REFERENCIA =
            new ReferenciaChamada(UUID.fromString("0f5a8f61-7c1e-4b0e-9a55-0c3c2f7d4e21"), null, null);

    private ServidorIncmTeste servidor;
    private RelogioAjustavel relogio;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        relogio = new RelogioAjustavel(Instant.parse("2026-11-13T09:00:00Z"));
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private TokenGeoconform token() {
        GeoconformProperties propriedades = new GeoconformProperties(servidor.url(), servidor.url() + CAMINHO_TOKEN,
                CLIENTE, SEGREDO, Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new TokenGeoconform(RestClient.create(), new ExecutorChamadasExternas(CircuitBreakerRegistry.ofDefaults(), evento -> { }),
                propriedades, relogio);
    }

    private static String resposta(String token, Long expiraEm) {
        return expiraEm == null ? "{\"access_token\": \"" + token + "\", \"token_type\": \"Bearer\"}"
                : "{\"access_token\": \"" + token + "\", \"token_type\": \"Bearer\", \"expires_in\": " + expiraEm + "}";
    }

    private static Map<String, String> formulario(String corpo) {
        return Arrays.stream(corpo.split("&")).map(par -> par.split("=", 2)).collect(Collectors.toMap(
                par -> URLDecoder.decode(par[0], StandardCharsets.UTF_8),
                par -> URLDecoder.decode(par[1], StandardCharsets.UTF_8)));
    }

    @Test
    void devePedirOTokenPorClientCredentialsComOAmbitoProcessosEscreverECredenciaisEmBasic() {
        servidor.responder(CAMINHO_TOKEN, 200, resposta(TOKEN, 3600L));

        TokenGeoconform.Autenticacao autenticacao = token().obter(null, REFERENCIA);

        assertThat(autenticacao.token()).isEqualTo(TOKEN);
        assertThat(autenticacao.falha()).isNull();
        assertThat(servidor.pedidos()).hasSize(1);
        ServidorIncmTeste.PedidoRecebido pedido = servidor.pedidos().getFirst();
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.cabecalhos().getFirst("Content-Type")).startsWith("application/x-www-form-urlencoded");
        assertThat(formulario(pedido.corpo())).containsOnly(Map.entry("grant_type", "client_credentials"),
                Map.entry("scope", "processos:escrever"));
        String basic = pedido.cabecalhos().getFirst("Authorization");
        assertThat(basic).startsWith("Basic ");
        assertThat(new String(Base64.getDecoder().decode(basic.substring(6)), StandardCharsets.UTF_8))
                .isEqualTo(CLIENTE + ":" + SEGREDO);
        assertThat(pedido.corpo()).doesNotContain(SEGREDO);
    }

    @Test
    void deveReutilizarOTokenAte30SegundosAntesDeExpirarEDepoisPedirOutro() {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, resposta(TOKEN, 3600L));
        servidor.responder(CAMINHO_TOKEN, 200, resposta(TOKEN_NOVO, 3600L));
        TokenGeoconform token = token();

        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN);
        relogio.avancar(Duration.ofSeconds(3569));
        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN);
        assertThat(servidor.pedidos()).hasSize(1);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN_NOVO);
        assertThat(servidor.pedidos()).hasSize(2);
    }

    @Test
    void devePedirNovoTokenQuandoOAtualFoiRecusado() {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, resposta(TOKEN, 3600L));
        servidor.responder(CAMINHO_TOKEN, 200, resposta(TOKEN_NOVO, 3600L));
        TokenGeoconform token = token();

        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN);
        assertThat(token.obter(TOKEN, REFERENCIA).token()).isEqualTo(TOKEN_NOVO);
        assertThat(token.obter(TOKEN, REFERENCIA).token()).isEqualTo(TOKEN_NOVO);
        assertThat(servidor.pedidos()).hasSize(2);
    }

    @Test
    void semExpiresInServeSoAOperacaoQueOPediu() {
        servidor.responder(CAMINHO_TOKEN, 200, resposta(TOKEN, null));
        TokenGeoconform token = token();

        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN);
        assertThat(token.obter(null, REFERENCIA).token()).isEqualTo(TOKEN);
        assertThat(servidor.pedidos()).hasSize(2);
    }

    @Test
    void deveDevolverAFalhaDoPedidoComUmaLinhaErrorSemAsCredenciais(LogJson log) {
        servidor.responder(CAMINHO_TOKEN, 500, "{\"error\": \"server_error\", \"detalhe\": \"" + SEGREDO + "\"}");

        TokenGeoconform.Autenticacao autenticacao = token().obter(null, REFERENCIA);

        assertThat(autenticacao.token()).isNull();
        assertThat(autenticacao.falha().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(autenticacao.falha().httpStatus()).isEqualTo(500);
        assertThat(log.todas()).contains("Pedido do token do GeoConform falhou")
                .contains(REFERENCIA.processoId().toString())
                .doesNotContain(SEGREDO).doesNotContain(CLIENTE).doesNotContain("server_error");
        assertThat(log.ultimaChamada("TOKEN").path("processoId").asText())
                .isEqualTo(REFERENCIA.processoId().toString());
    }

    @Test
    void deveDevolverErroHttpQuandoARespostaNaoTrazAccessToken() {
        servidor.responder(CAMINHO_TOKEN, 200, "{\"token_type\": \"Bearer\", \"expires_in\": 3600}");

        TokenGeoconform.Autenticacao autenticacao = token().obter(null, REFERENCIA);

        assertThat(autenticacao.token()).isNull();
        assertThat(autenticacao.falha().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(autenticacao.falha().httpStatus()).isEqualTo(200);
    }

    @Test
    void naoDeveEscreverOAccessTokenNemOSegredoNoLog(LogJson log) {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, resposta(TOKEN, 3600L));
        servidor.responder(CAMINHO_TOKEN, 401, "{\"error\": \"invalid_client\"}");
        TokenGeoconform token = token();

        TokenGeoconform.Autenticacao autenticacao = token.obter(null, REFERENCIA);
        token.obter(TOKEN, REFERENCIA);

        assertThat(autenticacao.toString()).doesNotContain(TOKEN);
        assertThat(log.ultimaChamada("TOKEN").path("cabecalhosPedido").path("Authorization").asText())
                .isEqualTo("***");
        assertThat(log.todas()).contains("GEOCONFORM")
                .doesNotContain(TOKEN).doesNotContain(SEGREDO).doesNotContain(CLIENTE)
                .doesNotContain(Base64.getEncoder().encodeToString((CLIENTE + ":" + SEGREDO).getBytes(StandardCharsets.UTF_8)))
                .doesNotContain("invalid_client");
    }

    static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
