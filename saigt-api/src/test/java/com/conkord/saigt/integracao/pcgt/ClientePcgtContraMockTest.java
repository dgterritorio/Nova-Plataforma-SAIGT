package com.conkord.saigt.integracao.pcgt;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cenários de `docker/pcgt-mock/README.md`; corre com `PCGT_MOCK_URL=http://localhost:8091`. */
@EnabledIfEnvironmentVariable(named = "PCGT_MOCK_URL", matches = ".+")
class ClientePcgtContraMockTest {

    private static final String URL_MOCK = System.getenv("PCGT_MOCK_URL");
    private static final String UTILIZADOR = "saigt-integracao";
    private static final String SENHA_MOCK = System.getenv().getOrDefault("PCGT_PASSWORD", "changeme");
    private static final String IDUS = "01.1111.01.000123.02.00077.01.031.01.20261110";
    private static final ReferenciaChamada REFERENCIA =
            new ReferenciaChamada(UUID.fromString("5c2d1e0f-3a4b-4c5d-8e6f-7a8b9c0d1e2f"), null, null);

    private static ClientePcgt criarCliente(String senha) {
        return new ClientePcgt(RestClient.builder(),
                new ExecutorChamadasExternas(CircuitBreakerRegistry.ofDefaults(), evento -> { }),
                RateLimiterRegistry.ofDefaults(),
                new PcgtProperties(URL_MOCK, UTILIZADOR, senha, Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    private static ClientePcgt criarCliente() {
        return criarCliente(SENHA_MOCK);
    }

    private static PedidoAtualizacaoProcessoPcgt atualizacao(String codigo, String idus) {
        return new PedidoAtualizacaoProcessoPcgt(codigo, idus, "1111", "Aviso", "1/2026", LocalDate.of(2026, 11, 10),
                "https://dre.exemplo.pt/aviso", null, null, null, null);
    }

    private static PedidoCriacaoProcessoPcgt criacao(String codigo, String idus) {
        return new PedidoCriacaoProcessoPcgt(codigo, null, null, "Alteração — PDM de teste", "01", "02", "1111", idus,
                new EntidadePcgt("CM-TESTE"), "Aviso", "1/2026", LocalDate.of(2026, 11, 10), null, null, null, null, null);
    }

    @Test
    void deveAutenticarComAContaDeTesteEAtualizarO00042() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042", IDUS), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().codigoProcesso()).isEqualTo("00042");
    }

    @Test
    void deveDevolver401ComOutraSenha() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente("senha-errada").atualizar(atualizacao("00042", IDUS),
                REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(401);
    }

    @Test
    void deveAtualizarQualquerProcessoEcoandoOCodigoEOIdus() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00007", IDUS), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().codigoProcesso()).isEqualTo("00007");
        assertThat(resultado.corpo().codigoIdus()).isEqualTo(IDUS);
    }

    @Test
    void deveDevolver404ComAMensagemDoContratoParaOCodigoReservado() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("99999", IDUS), REFERENCIA);

        assertThat(resultado.processoNaoExiste()).isTrue();
        assertThat(resultado.erro().code()).isEqualTo(404);
        assertThat(resultado.erro().message()).isEqualTo("Processo não encontrado para o codigoProcesso: 99999");
    }

    @Test
    void deveDevolver400NoPutSemCodigoProcesso() {
        String resposta = RestClient.create(URL_MOCK).put().uri("/api/processos")
                .header("Authorization", "Bearer token-de-teste")
                .contentType(MediaType.APPLICATION_JSON).body("{\"codigoIdus\": \"" + IDUS + "\"}")
                .exchange((pedido, respostaHttp) -> respostaHttp.getStatusCode().value() + " "
                        + new String(respostaHttp.getBody().readAllBytes(), StandardCharsets.UTF_8));

        assertThat(resposta).startsWith("400 ").contains("codigoProcesso");
    }

    @Test
    void deveDevolver400NoPutComIdusForaDoFormato() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042", "01.1111.invalido"),
                REFERENCIA);

        assertThat(resultado.chamada().httpStatus()).isEqualTo(400);
        assertThat(resultado.erro().code()).isEqualTo(400);
    }

    @Test
    void deveCriarComCodigoNovoEcoandoOCodigoEOIdSsaigt() {
        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().criar(criacao("00077", IDUS), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo().codigoProcesso()).isEqualTo("00077");
        assertThat(resultado.corpo().numeroProcesso()).isEqualTo("00077");
        assertThat(resultado.corpo().idSSAIGT()).isEqualTo("00077");
        assertThat(resultado.corpo().codigoIdus()).isEqualTo(IDUS);
    }

    @Test
    void deveDevolver400ComCodigoEMensagemNosPostsInvalidos() {
        ClientePcgt cliente = criarCliente();

        for (PedidoCriacaoProcessoPcgt pedido : new PedidoCriacaoProcessoPcgt[]{
                criacao("00042", IDUS), criacao("X".repeat(51), IDUS), criacao("00078", "01.1111.invalido")}) {
            ResultadoPcgt<ProcessoPcgt> resultado = cliente.criar(pedido, REFERENCIA);
            assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
            assertThat(resultado.chamada().httpStatus()).isEqualTo(400);
            assertThat(resultado.erro().code()).isEqualTo(400);
            assertThat(resultado.erro().message()).isNotBlank();
        }
    }
}
