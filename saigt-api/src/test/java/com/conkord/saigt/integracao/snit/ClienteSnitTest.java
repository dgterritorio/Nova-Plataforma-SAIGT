package com.conkord.saigt.integracao.snit;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.controller.core.TraceIdFilter;
import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(LogJson.Extensao.class)
class ClienteSnitTest {

    private static final String CHAVE = "chave-snit-teste-4d2b";
    private static final String CAMINHO = "/api/integrations/saigt/instruments";

    private static final String PESQUISA = """
            {"items": [{"idIgt": 1001, "typeCode": "PDM", "designation": "PDM de Sintra (SNIT)",
                        "validityStatus": "in_force", "wmsUrl": "https://snit.example/wms/1001", "campoNovo": 1}],
             "total": 1, "page": 1, "pageSize": 20}
            """;
    private static final String DETALHE = """
            {"idIgt": 1001, "typeCode": "PDM", "designation": "PDM de Sintra (SNIT)", "validityStatus": "in_force",
             "municipalities": [{"dtcc": "1111", "name": "Sintra"}], "activeDynamic": {"id": 50012}}
            """;

    private ServidorIncmTeste servidor;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        registry = CircuitBreakerRegistry.ofDefaults();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private ClienteSnit criarCliente(String baseUrl, String chave, Duration readTimeout) {
        SnitProperties propriedades = new SnitProperties(baseUrl, chave, Duration.ofSeconds(2), readTimeout);
        return new ClienteSnit(RestClient.builder(), new ExecutorChamadasExternas(registry, evento -> { }), propriedades);
    }

    private ClienteSnit criarCliente() {
        return criarCliente(servidor.url(), CHAVE, Duration.ofSeconds(5));
    }

    @Test
    void devePesquisarEnviandoAChaveOsFiltrosEOEstadoEmVigor() {
        servidor.responder(CAMINHO, 200, PESQUISA);

        PaginaSnit pagina = criarCliente().pesquisar("PDM", "1111", 2, 10);

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.items()).containsExactly(new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force"));
        assertThat(servidor.pedidos()).singleElement().satisfies(pedido -> {
            assertThat(pedido.caminho()).isEqualTo(CAMINHO);
            assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
            assertThat(pedido.consulta()).contains("status=in_force", "page=2", "pageSize=10", "type=PDM", "municipality=1111");
        });
    }

    @Test
    void deveOmitirOsFiltrosSemValor() {
        servidor.responder(CAMINHO, 200, PESQUISA);

        criarCliente().pesquisar(null, " ", 1, 20);

        assertThat(servidor.pedidos()).singleElement().satisfies(pedido ->
                assertThat(pedido.consulta()).doesNotContain("type=").doesNotContain("municipality="));
    }

    @Test
    void deveCodificarOFiltroDoUtilizadorEmVezDeOInterpretarComoTemplate() {
        servidor.responder(CAMINHO, 200, PESQUISA);

        criarCliente().pesquisar("PDM", "{x}&page=99", 1, 20);

        assertThat(servidor.pedidos()).singleElement().satisfies(pedido -> {
            assertThat(pedido.consulta()).contains("municipality=%7Bx%7D%26page%3D99");
            assertThat(pedido.consulta()).doesNotContain("page=99");
        });
    }

    @Test
    void deveObterODetalheDeUmIgtIgnorandoOsCamposQueNaoUsa() {
        servidor.responder(CAMINHO + "/1001", 200, DETALHE);

        Optional<IgtSnit> igt = criarCliente().obter(1001);

        assertThat(igt).contains(new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force",
                List.of(new MunicipioIgtSnit("1111", "Sintra"))));
        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
    }

    @Test
    void deveLerOsConcelhosDoDetalheIgnorandoOsCamposDesconhecidosEAusenciaDeMunicipalities() {
        servidor.responder(CAMINHO + "/1006", 200, """
                {"idIgt": 1006, "typeCode": "PDM", "designation": "PDM Intermunicipal", "validityStatus": "in_force",
                 "municipalities": [{"dtcc": "1111", "name": "Sintra", "campoNovo": 1}, {"dtcc": "1105", "name": "Cascais"}]}
                """);
        servidor.responder(CAMINHO + "/1007", 200, """
                {"idIgt": 1007, "typeCode": "PDM", "designation": "PDM Nacional", "validityStatus": "in_force"}
                """);

        ClienteSnit cliente = criarCliente();

        assertThat(cliente.obter(1006).orElseThrow().municipalities())
                .containsExactly(new MunicipioIgtSnit("1111", "Sintra"), new MunicipioIgtSnit("1105", "Cascais"));
        assertThat(cliente.obter(1007).orElseThrow().municipalities()).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoOSnitNaoConheceOIgtSemContarComoFalhaDoCircuito() {
        ClienteSnit cliente = criarCliente();
        CircuitBreaker circuito = registry.circuitBreaker(ClienteSnit.CIRCUITO_CONSULTA);

        assertThat(cliente.obter(999)).isEmpty();

        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuito.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void deveFalharComIndisponibilidadeQuandoOSnitResponde500() {
        servidor.responder(CAMINHO, 500, null);

        assertThatThrownBy(() -> criarCliente().pesquisar("PDM", null, 1, 20))
                .isInstanceOf(SnitIndisponivelException.class)
                .extracting("codigo").isEqualTo("INTEGRACAO_INDISPONIVEL");
    }

    @Test
    void deveFalharComIndisponibilidadeQuandoOSnitRecusaAChave() {
        servidor.responder(CAMINHO + "/1001", 401, null);

        assertThatThrownBy(() -> criarCliente().obter(1001)).isInstanceOf(SnitIndisponivelException.class);
        assertThat(registry.circuitBreaker(ClienteSnit.CIRCUITO_CONSULTA).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveFalharComIndisponibilidadeNoTimeoutDeLeitura() {
        servidor.reterResposta(CAMINHO);
        ClienteSnit cliente = criarCliente(servidor.url(), CHAVE, Duration.ofMillis(300));

        assertThatThrownBy(() -> cliente.pesquisar("PDM", null, 1, 20)).isInstanceOf(SnitIndisponivelException.class);
    }

    @Test
    void deveFalharQuandoARespostaNaoTemItens() {
        servidor.responder(CAMINHO, 200, "{\"total\": 0}");

        assertThatThrownBy(() -> criarCliente().pesquisar("PDM", null, 1, 20)).isInstanceOf(SnitIndisponivelException.class);
    }

    @Test
    void deveFalharSemChamarOSnitComOCircuitoAberto(LogJson log) {
        ClienteSnit cliente = criarCliente();
        registry.circuitBreaker(ClienteSnit.CIRCUITO_CONSULTA).transitionToOpenState();

        assertThatThrownBy(() -> cliente.pesquisar("PDM", null, 1, 20)).isInstanceOf(SnitIndisponivelException.class);
        assertThatThrownBy(() -> cliente.obter(1001)).isInstanceOf(SnitIndisponivelException.class);

        assertThat(servidor.pedidos()).isEmpty();
        assertThat(log.ultimaChamada("PESQUISAR_INSTRUMENTOS").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
        assertThat(log.ultimaChamada("OBTER_INSTRUMENTO").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @ParameterizedTest
    @EnumSource(ListaReferenciaSnit.class)
    void deveObterATabelaDeReferenciaPeloCaminhoDaListaComAChaveERegistarAOperacao(ListaReferenciaSnit lista,
                                                                                 LogJson log) {
        servidor.responder(lista.caminho(), 200, "[{\"" + lista.chaveCodigo() + "\": \"A1\", \"" + lista.chaveNome() + "\": \"Um\"}, "
                + "{\"" + lista.chaveCodigo() + "\": 2, \"campoNovo\": true}]");

        List<JsonNode> registos = criarCliente().obterTabelaReferencia(lista);

        assertThat(registos).hasSize(2);
        assertThat(registos.get(0).get(lista.chaveCodigo()).asText()).isEqualTo("A1");
        assertThat(registos.get(0).get(lista.chaveNome()).asText()).isEqualTo("Um");
        assertThat(registos.get(1).get("campoNovo").asBoolean()).isTrue();
        assertThat(servidor.pedidos()).singleElement().satisfies(pedido -> {
            assertThat(pedido.caminho()).isEqualTo(lista.caminho());
            assertThat(pedido.consulta()).isNull();
            assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        });
        JsonNode evento = log.ultimaChamada("OBTER_REFERENCIA_" + lista.name());
        assertThat(evento.path("sistema").asText()).isEqualTo("SNIT");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + lista.caminho());
        assertThat(evento.path("resultado").asText()).isEqualTo("OK");
        assertThat(log.todas()).doesNotContain(CHAVE);
    }

    @Test
    void deveFalharQuandoATabelaDeReferenciaNaoVemNumaLista() {
        servidor.responder(ListaReferenciaSnit.NUT2.caminho(), 200, "{\"items\": [{\"code\": \"NORTE\", \"label\": \"Norte\"}]}");

        assertThatThrownBy(() -> criarCliente().obterTabelaReferencia(ListaReferenciaSnit.NUT2))
                .isInstanceOf(SnitIndisponivelException.class)
                .hasMessage("Resposta do SNIT inválida.");
    }

    @Test
    void deveFalharComIndisponibilidadeQuandoOSnitFalhaNaTabelaDeReferencia() {
        servidor.responder(ListaReferenciaSnit.CLASSES.caminho(), 503, null);

        assertThatThrownBy(() -> criarCliente().obterTabelaReferencia(ListaReferenciaSnit.CLASSES))
                .isInstanceOf(SnitIndisponivelException.class)
                .extracting("codigo").isEqualTo("INTEGRACAO_INDISPONIVEL");
    }

    @Test
    void deveFalharSemPedirATabelaDeReferenciaComOCircuitoAberto(LogJson log) {
        ClienteSnit cliente = criarCliente();
        registry.circuitBreaker(ClienteSnit.CIRCUITO_CONSULTA).transitionToOpenState();

        assertThatThrownBy(() -> cliente.obterTabelaReferencia(ListaReferenciaSnit.MUNICIPALITIES))
                .isInstanceOf(SnitIndisponivelException.class);

        assertThat(servidor.pedidos()).isEmpty();
        assertThat(log.ultimaChamada("OBTER_REFERENCIA_MUNICIPALITIES").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveFalharSemChamarOSnitQuandoOEnderecoNaoEstaConfigurado() {
        ClienteSnit cliente = criarCliente("", CHAVE, Duration.ofSeconds(5));

        assertThatThrownBy(() -> cliente.pesquisar("PDM", null, 1, 20)).isInstanceOf(SnitIndisponivelException.class);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void naoDeveEscreverAChaveNoLogNemNaRepresentacaoDasPropriedades(LogJson log) {
        servidor.responder(CAMINHO, 200, PESQUISA);

        criarCliente().pesquisar("PDM", null, 1, 20);

        assertThat(log.todas()).doesNotContain(CHAVE);
        assertThat(log.ultimaChamada("PESQUISAR_INSTRUMENTOS").path("resultado").asText()).isEqualTo("OK");
        assertThat(new SnitProperties("http://snit", CHAVE, Duration.ofSeconds(1), Duration.ofSeconds(1)).toString())
                .doesNotContain(CHAVE);
    }

    @Test
    void devePropagarOXTraceIdDoPedidoHttpEEscreveLoNaLinhaDeIntegracao(LogJson log) throws Exception {
        servidor.responder(CAMINHO, 200, PESQUISA);
        ClienteSnit cliente = criarCliente();
        MockHttpServletRequest pedidoHttp = new MockHttpServletRequest("GET", "/api/v1/instrumentos/snit");
        pedidoHttp.addHeader("X-Trace-Id", "trace-snit-157");

        new TraceIdFilter().doFilter(pedidoHttp, new MockHttpServletResponse(),
                (pedido, resposta) -> cliente.pesquisar("PDM", "1111", 1, 20));

        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Trace-Id")).isEqualTo("trace-snit-157");
        JsonNode evento = log.ultimaChamada("PESQUISAR_INSTRUMENTOS");
        assertThat(evento.path("correlationId").asText()).isEqualTo("trace-snit-157");
        assertThat(evento.path("sistema").asText()).isEqualTo("SNIT");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(200);
        assertThat(evento.path("metodo").asText()).isEqualTo("GET");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + CAMINHO);
        assertThat(evento.path("cabecalhosPedido").path("X-Saigt-Api-Key").asText()).isEqualTo("***");
        assertThat(evento.path("resultado").asText()).isEqualTo("OK");
        assertThat(log.todas()).doesNotContain("municipality");
    }
}
