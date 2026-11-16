package com.conkord.saigt.integracao.snit;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.controller.core.TraceIdFilter;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@ExtendWith(LogJson.Extensao.class)
class ClienteSnitRegistoTest {

    private static final String CHAVE = "chave-snit-registo-teste-9c1e";
    private static final String CAMINHO = "/api/integrations/saigt/publications";
    private static final String CAMINHO_INSTRUMENTOS = "/api/integrations/saigt/instruments";
    private static final UUID PROCESSO_ID = UUID.fromString("0f6c2a1e-8b3d-4e5f-9a7c-1d2e3f4a5b6c");
    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(PROCESSO_ID, null, null);
    private static final String DESIGNACAO = "Correção Material do Plano Diretor Municipal de Exemplo";
    private static final String DIPLOMA = "4321/2026";

    private static final String RECEBIDA = """
            {"ssaigtId": 2490, "receivedAt": "2026-11-04T10:15:30Z", "idIgt": null, "publicationOrder": null,
             "activeDynamicsCount": null, "campoNovo": true}
            """;
    private static final String RECEBIDA_REPETICAO = """
            {"ssaigtId": 2490, "receivedAt": "2026-11-04T10:15:30Z", "idIgt": 1001, "publicationOrder": 2,
             "activeDynamicsCount": 1}
            """;
    private static final String CONFLITO = """
            {"message": "Publication conflict for SSAIGT id.", "ssaigtId": 2490, "existingDiploma": "4321/2026",
             "incomingDiploma": "9999/2099"}
            """;
    private static final String PESQUISA = """
            {"items": [{"idIgt": 1001, "typeCode": "PDM", "designation": "PDM de Exemplo (SNIT)",
                        "validityStatus": "in_force"}], "total": 1, "page": 1, "pageSize": 20}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServidorIncmTeste servidor;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        usarRegistry(CircuitBreakerConfig.custom());
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    /** Espelha `instances.snit-registo.ignore-exceptions` das properties; o `snit-consulta` fica sem ela. */
    private void usarRegistry(CircuitBreakerConfig.Builder configRegisto) {
        registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker(ClienteSnit.CIRCUITO_REGISTO,
                configRegisto.ignoreExceptions(HttpClientErrorException.class).build());
    }

    private ClienteSnit criarCliente(String baseUrl, Duration readTimeout) {
        SnitProperties propriedades = new SnitProperties(baseUrl, CHAVE, Duration.ofSeconds(2), readTimeout);
        return new ClienteSnit(RestClient.builder(), new ExecutorChamadasExternas(registry, evento -> { }), propriedades);
    }

    private ClienteSnit criarCliente() {
        return criarCliente(servidor.url(), Duration.ofSeconds(5));
    }

    private CircuitBreaker circuitoRegisto() {
        return registry.circuitBreaker(ClienteSnit.CIRCUITO_REGISTO);
    }

    private static PublicacaoSnit pedidoCompleto() {
        return new PublicacaoSnit(2490L, 1, 11, 3, DESIGNACAO, "NORTE", "9999", "Sumário fictício da publicação",
                "00", new PublicacaoDrSnit(DIPLOMA, "2", "188", LocalDate.of(2026, 9, 27),
                "https://dr.exemplo.pt/aviso/4321-2026"),
                new VerificacaoSnit(Instant.parse("2026-09-27T00:00:00Z"), "utilizador.teste"));
    }

    private static PublicacaoSnit pedidoSoComObrigatorios() {
        return new PublicacaoSnit(2490L, 1, 11, 3, DESIGNACAO, "NORTE", "9999", null, null,
                new PublicacaoDrSnit(DIPLOMA, null, null, LocalDate.of(2026, 9, 27), null), null);
    }

    @Test
    void deveRegistarAPublicacaoComAChaveEOsNomesDoContratoQuandoOSnitResponde201() throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDA);

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado =
                criarCliente().registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo()).isEqualTo(
                new PublicacaoRecebidaSnit(2490L, Instant.parse("2026-11-04T10:15:30Z"), null, null, null));
        ServidorIncmTeste.PedidoRecebido pedido = servidor.pedidos().getFirst();
        assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        assertThat(pedido.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        JsonNode corpo = objectMapper.readTree(pedido.corpo());
        assertThat(corpo.get("ssaigtId").asLong()).isEqualTo(2490L);
        assertThat(corpo.get("typeId").asInt()).isEqualTo(1);
        assertThat(corpo.get("procedureId").asInt()).isEqualTo(11);
        assertThat(corpo.get("classId").asInt()).isEqualTo(3);
        assertThat(corpo.get("designation").asText()).isEqualTo(DESIGNACAO);
        assertThat(corpo.get("nut2Code").asText()).isEqualTo("NORTE");
        assertThat(corpo.get("municipalityDtcc").asText()).isEqualTo("9999");
        assertThat(corpo.get("summary").asText()).isEqualTo("Sumário fictício da publicação");
        assertThat(corpo.get("publicationStatus").asText()).isEqualTo("00");
        assertThat(corpo.get("publication").get("diploma").asText()).isEqualTo(DIPLOMA);
        assertThat(corpo.get("publication").get("series").asText()).isEqualTo("2");
        assertThat(corpo.get("publication").get("number").asText()).isEqualTo("188");
        assertThat(corpo.get("publication").get("publicationDate").asText()).isEqualTo("2026-09-27");
        assertThat(corpo.get("publication").get("hyperlink").asText()).isEqualTo("https://dr.exemplo.pt/aviso/4321-2026");
        assertThat(corpo.get("verification").get("verifiedAt").asText()).isEqualTo("2026-09-27T00:00:00Z");
        assertThat(corpo.get("verification").get("verifiedBy").asText()).isEqualTo("utilizador.teste");
    }

    @Test
    void deveOmitirOsCamposOpcionaisSemValorNoPedido() throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDA);

        criarCliente().registarPublicacao(pedidoSoComObrigatorios(), REFERENCIA);

        String json = servidor.pedidos().getFirst().corpo();
        JsonNode corpo = objectMapper.readTree(json);
        assertThat(json).doesNotContain("null");
        assertThat(corpo.has("summary")).isFalse();
        assertThat(corpo.has("publicationStatus")).isFalse();
        assertThat(corpo.has("verification")).isFalse();
        assertThat(corpo.get("publication").has("series")).isFalse();
        assertThat(corpo.get("publication").has("number")).isFalse();
        assertThat(corpo.get("publication").has("hyperlink")).isFalse();
        assertThat(corpo.get("publication").get("publicationDate").asText()).isEqualTo("2026-09-27");
    }

    @Test
    void deveDevolverOCorpoSemFalhaNaRepeticaoIdempotenteQuandoOSnitResponde200() {
        servidor.responder(CAMINHO, 200, RECEBIDA_REPETICAO);

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado =
                criarCliente().registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo()).isEqualTo(
                new PublicacaoRecebidaSnit(2490L, Instant.parse("2026-11-04T10:15:30Z"), 1001L, 2, 1));
    }

    @Test
    void deveDevolverErroHttp409SemContarParaOCircuitoQuandoODiplomaEDiferente() {
        servidor.responder(CAMINHO, 409, CONFLITO);

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado =
                criarCliente().registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(409);
        assertThat(resultado.corpo()).isNull();
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401})
    void deveDevolverErroHttpComOCodigoSemContarParaOCircuitoNoPedidoInvalidoOuNasCredenciais(int codigo) {
        servidor.responder(CAMINHO, codigo, null);

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado =
                criarCliente().registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void deveDevolverErroHttpContarParaOCircuitoESemNovaTentativaQuandoOSnitResponde5xx(int codigo) {
        servidor.responder(CAMINHO, codigo, null);

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado =
                criarCliente().registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveDevolverTimeoutContarParaOCircuitoESemNovaTentativa() {
        servidor.reterResposta(CAMINHO);
        ClienteSnit cliente = criarCliente(servidor.url(), Duration.ofMillis(300));

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(resultado.httpStatus()).isNull();
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveAbrirOCircuitoDoRegistoCom5xxMasNaoCom4xx() {
        usarRegistry(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMinutes(1)));
        ClienteSnit cliente = criarCliente();

        servidor.responder(CAMINHO, 409, CONFLITO);
        cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        servidor.responder(CAMINHO, 400, null);
        cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        servidor.responder(CAMINHO, 401, null);
        cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        assertThat(circuitoRegisto().getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        servidor.responder(CAMINHO, 500, null);
        cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        assertThat(circuitoRegisto().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(cliente.registarPublicacao(pedidoCompleto(), REFERENCIA).falhaTecnica())
                .isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(servidor.pedidos()).hasSize(5);
    }

    @Test
    void deveDevolverCircuitoAbertoSemPedidoEManterAsConsultasPeloSnitConsulta(LogJson log) {
        servidor.responder(CAMINHO_INSTRUMENTOS, 200, PESQUISA);
        ClienteSnit cliente = criarCliente();
        circuitoRegisto().transitionToOpenState();

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);
        PaginaSnit pagina = cliente.pesquisar("PDM", null, 1, 20);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(resultado.corpo()).isNull();
        assertThat(pagina.items()).hasSize(1);
        assertThat(servidor.pedidos()).singleElement()
                .satisfies(pedido -> assertThat(pedido.caminho()).isEqualTo(CAMINHO_INSTRUMENTOS));
        assertThat(registry.circuitBreaker(ClienteSnit.CIRCUITO_CONSULTA).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(log.ultimaChamada("REGISTAR_PUBLICACAO").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoQuandoOEnderecoEstaVazio() {
        ClienteSnit cliente = criarCliente("", Duration.ofSeconds(5));

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = cliente.registarPublicacao(pedidoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(resultado.httpStatus()).isNull();
        assertThat(servidor.pedidos()).isEmpty();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void deveRecusarPedidoNuloSemChamarOSnit(LogJson log) {
        ClienteSnit cliente = criarCliente();

        assertThatNullPointerException().isThrownBy(() -> cliente.registarPublicacao(null, REFERENCIA))
                .withMessage("pedido");

        assertThat(servidor.pedidos()).isEmpty();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(log.chamadas("REGISTAR_PUBLICACAO")).isEmpty();
    }

    @Test
    void deveEscreverALinhaDeIntegracaoSemCorposNemChave(LogJson log) throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDA);
        ClienteSnit cliente = criarCliente();
        MockHttpServletRequest pedidoHttp = new MockHttpServletRequest("POST", "/api/v1/processos");
        pedidoHttp.addHeader("X-Trace-Id", "trace-snit-243");
        AtomicReference<ResultadoChamadaExterna<PublicacaoRecebidaSnit>> resultado = new AtomicReference<>();

        new TraceIdFilter().doFilter(pedidoHttp, new MockHttpServletResponse(),
                (pedido, resposta) -> resultado.set(cliente.registarPublicacao(pedidoCompleto(), REFERENCIA)));

        assertThat(resultado.get().temFalhaTecnica()).isFalse();
        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Trace-Id")).isEqualTo("trace-snit-243");
        JsonNode evento = log.ultimaChamada("REGISTAR_PUBLICACAO");
        assertThat(evento.path("sistema").asText()).isEqualTo("SNIT");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(201);
        assertThat(evento.path("metodo").asText()).isEqualTo("POST");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + CAMINHO);
        assertThat(evento.path("correlationId").asText()).isEqualTo("trace-snit-243");
        assertThat(evento.path("processoId").asText()).isEqualTo(PROCESSO_ID.toString());
        assertThat(evento.path("cabecalhosPedido").path("X-Saigt-Api-Key").asText()).isEqualTo("***");
        assertThat(evento.path("duracaoMs").isIntegralNumber()).isTrue();
        assertThat(evento.path("resultado").asText()).isEqualTo("OK");
        assertThat(log.todas()).doesNotContain(CHAVE).doesNotContain(DESIGNACAO).doesNotContain(DIPLOMA)
                .doesNotContain("2026-11-04T10:15:30Z");
    }
}
