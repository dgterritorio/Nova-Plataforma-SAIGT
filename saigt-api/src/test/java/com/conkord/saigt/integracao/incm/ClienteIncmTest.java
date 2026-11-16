package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.controller.core.TraceIdFilter;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(LogJson.Extensao.class)
class ClienteIncmTest {

    private static final String CHAVE = "chave-servico-teste-7f3a";
    private static final UUID PROCESSO_ID = UUID.fromString("5b0c9f4e-2d7a-4c1e-9f3b-8a6d2e1c4b70");
    private static final long ACT_IDENTIFIER = 900000000000001L;

    private static final String SUBMIT_SUCESSO = """
            {"IsSuccess": true, "ErrorMessageList": [], "ActIdentifier": 900000000000001}
            """;
    private static final String VALIDATE_USER_RECUSADO = """
            {"IsSuccess": false, "ErrorMessage": "User email address and/or password not valid", "UserInformation": null}
            """;
    private static final String DETALHES_PUBLICADO = """
            {
              "IsSuccess": true,
              "ErrorMessage": "",
              "ProcessingStatus": "SubmissionAccepted",
              "CreatedOn": "2026-10-23T10:00:00.000Z",
              "UpdatedOn": "2026-10-24T09:00:00.000Z",
              "ActReferenceIdentifier": "DR-2026-000001",
              "FormURL": "https://exemplo.pt/formulario/DR-2026-000001",
              "ActJSON": "ewogICJER1RBY3QiOiB7fQp9",
              "ActStatus": "Publicado",
              "ActValidationErrorMessageList": [],
              "ActLogList": [{"ActStatus": "Em edição", "CreatedOn": "2026-10-23T10:05:00.000Z"}],
              "Publication": {
                "ActPDFURL": "https://exemplo.pt/dr/2026/000001/ato.pdf",
                "ActTitle": "Aviso de início do procedimento",
                "ActURL": "https://exemplo.pt/dr/2026/000001",
                "DiarioRepublicaTitle": "Diário da República, 2.ª série",
                "ActPublicationDate": "2026-10-24"
              }
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
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

    private ClienteIncm criarCliente(String baseUrl, Duration readTimeout) {
        IncmProperties propriedades = new IncmProperties(baseUrl, CHAVE, Duration.ofSeconds(2), readTimeout);
        return new ClienteIncm(RestClient.builder(), new ExecutorChamadasExternas(registry, evento -> { }), propriedades);
    }

    private ClienteIncm criarCliente() {
        return criarCliente(servidor.url(), Duration.ofSeconds(5));
    }

    private static SubmitNewActRequest pedidoSerie2(String sumario) {
        return new SubmitNewActRequest(new SubmitNewActRequest.DgtAct("2015482", null,
                new SubmitNewActRequest.DiarioRepublicaSeries2Act(1234567891234568L, 1234567891234567L, "full-text",
                        sumario, LocalDate.of(2025, 12, 31), "Artigo 76.º do RJIGT"),
                List.of(), List.of("https://exemplo.pt/publico/token/aviso.pdf")));
    }

    @Test
    void deveEnviarServiceKeyEmTodasAsOperacoesSemOEscreverNoLog(LogJson log) {
        ClienteIncm cliente = criarCliente();
        servidor.responder("/ValidateUser", 200, VALIDATE_USER_RECUSADO);
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);
        servidor.responder("/GetActDetails", 200, DETALHES_PUBLICADO);

        cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");
        cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        cliente.obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER);

        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho)
                .containsExactly("/ValidateUser", "/SubmitNewAct", "/GetActDetails");
        assertThat(servidor.pedidos()).allSatisfy(pedido ->
                assertThat(pedido.cabecalhos().getFirst("ServiceKey")).isEqualTo(CHAVE));
        assertThat(log.ultimaChamada("GET_ACT_DETAILS").path("cabecalhosPedido").path("ServiceKey").asText())
                .isEqualTo("***");
        assertThat(log.todas()).doesNotContain(CHAVE);
    }

    @Test
    void deveRegistarARespostaDoEnvioEDaConsultaSemOActJsonNemOValidateUser(LogJson log) {
        ClienteIncm cliente = criarCliente();
        servidor.responder("/ValidateUser", 200, VALIDATE_USER_RECUSADO);
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);
        servidor.responder("/GetActDetails", 200, DETALHES_PUBLICADO);

        cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");
        cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        GetActDetailsResponse detalhes = cliente.obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER).corpo();

        assertThat(detalhes.actJson()).isEqualTo("ewogICJER1RBY3QiOiB7fQp9");
        assertThat(log.ultimaChamada("VALIDATE_USER").has("resposta")).isFalse();
        assertThat(log.ultimaChamada("SUBMIT_NEW_ACT").path("resposta").asText())
                .startsWith("{\"IsSuccess\":true,").contains("\"ActIdentifier\":" + ACT_IDENTIFIER);
        assertThat(log.ultimaChamada("GET_ACT_DETAILS").path("resposta").asText())
                .startsWith("{\"IsSuccess\":true,")
                .contains("\"ActStatus\":\"Publicado\"", "\"CreatedOn\":\"2026-10-23T10:00:00Z\"")
                .doesNotContain("ActJSON", "ewogICJER1RBY3QiOiB7fQp9");
        assertThat(log.todas()).doesNotContain("ewogICJER1RBY3QiOiB7fQp9");
    }

    @Test
    void deveManterServiceKeyForaDoLogNasFalhasTecnicas(LogJson log) {
        servidor.responder("/SubmitNewAct", 500, null);
        servidor.reterResposta("/GetActDetails");

        criarCliente().submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        criarCliente(servidor.url(), Duration.ofMillis(300)).obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER);
        criarCliente(ServidorIncmTeste.urlInacessivel(), Duration.ofSeconds(5)).obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER);

        assertThat(log.chamadas("SUBMIT_NEW_ACT")).extracting(evento -> evento.path("resultado").asText())
                .containsExactly("ERRO_HTTP");
        assertThat(log.chamadas("GET_ACT_DETAILS")).extracting(evento -> evento.path("resultado").asText())
                .containsExactly("TIMEOUT", "ERRO_REDE");
        assertThat(log.todas()).doesNotContain(CHAVE);
    }

    @Test
    void deveEscreverLinhaDeIntegracaoComCorrelationIdDoPedidoHttp(LogJson log) throws Exception {
        ClienteIncm cliente = criarCliente();
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);
        MockHttpServletRequest pedidoHttp = new MockHttpServletRequest("POST", "/api/v1/processos/x/enviar-incm");
        pedidoHttp.addHeader("X-Trace-Id", "trace-incm-75");

        new TraceIdFilter().doFilter(pedidoHttp, new MockHttpServletResponse(),
                (pedido, resposta) -> cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso")));

        JsonNode evento = log.ultimaChamada("SUBMIT_NEW_ACT");
        assertThat(evento.path("correlationId").asText()).isEqualTo("trace-incm-75");
        assertThat(evento.path("processoId").asText()).isEqualTo(PROCESSO_ID.toString());
        assertThat(evento.path("sistema").asText()).isEqualTo("INCM");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(200);
        assertThat(evento.path("metodo").asText()).isEqualTo("POST");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + "/SubmitNewAct");
        assertThat(evento.path("resultado").asText()).isEqualTo("OK");
        assertThat(evento.path("duracaoMs").isIntegralNumber()).isTrue();
    }

    @Test
    void deveDevolverTimeoutSemLancarExcecaoQuandoAIncmNaoResponde() {
        servidor.reterResposta("/SubmitNewAct");
        ClienteIncm cliente = criarCliente(servidor.url(), Duration.ofMillis(300));
        AtomicReference<ResultadoChamadaIncm<SubmitNewActResponse>> resultado = new AtomicReference<>();

        assertThatCode(() -> resultado.set(cliente.submeterAto(PROCESSO_ID, pedidoSerie2("[MOCK:TIMEOUT] Aviso"))))
                .doesNotThrowAnyException();

        assertThat(resultado.get().falhaTecnica()).isEqualTo(FalhaTecnicaIncm.TIMEOUT);
        assertThat(resultado.get().httpStatus()).isNull();
        assertThat(resultado.get().corpo()).isNull();
        assertThat(resultado.get().duracaoMs()).isBetween(250L, 5000L);
    }

    @Test
    void deveDevolverErroHttpComOCodigoQuandoAIncmResponde500() {
        servidor.responder("/SubmitNewAct", 500, null);

        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente().submeterAto(PROCESSO_ID, pedidoSerie2("[MOCK:HTTP500] Aviso"));

        assertThat(resultado.httpStatus()).isEqualTo(500);
        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP);
        assertThat(resultado.corpo()).isNull();
    }

    @Test
    void deveDevolverErroHttpQuandoAIncmResponde404() {
        ResultadoChamadaIncm<GetActDetailsResponse> resultado = criarCliente().obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER);

        assertThat(resultado.httpStatus()).isEqualTo(404);
        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP);
    }

    @Test
    void deveDevolverErroHttpQuandoUmaResposta2xxNaoSeLeComoOContrato() {
        servidor.responder("/GetActDetails", 200, "isto não é JSON");

        ResultadoChamadaIncm<GetActDetailsResponse> resultado = criarCliente().obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP);
        assertThat(resultado.corpo()).isNull();
    }

    @Test
    void deveDevolverCircuitoAbertoNoEnvioENaConsultaSemChamarAIncm(LogJson log) {
        ClienteIncm cliente = criarCliente();
        registry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).transitionToOpenState();

        List<ResultadoChamadaIncm<?>> resultados = List.of(
                cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso")),
                cliente.obterDetalhesAto(PROCESSO_ID, ACT_IDENTIFIER));

        assertThat(resultados).allSatisfy(resultado -> {
            assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.CIRCUITO_ABERTO);
            assertThat(resultado.httpStatus()).isNull();
        });
        assertThat(servidor.pedidos()).isEmpty();
        assertThat(log.ultimaChamada("SUBMIT_NEW_ACT").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
        assertThat(log.ultimaChamada("GET_ACT_DETAILS").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveDevolverCircuitoAbertoNoLoginSemChamarAIncm(LogJson log) {
        ClienteIncm cliente = criarCliente();
        registry.circuitBreaker(ClienteIncm.CIRCUITO_AUTENTICACAO).transitionToOpenState();

        ResultadoChamadaIncm<ValidateUserResponse> resultado = cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.CIRCUITO_ABERTO);
        assertThat(resultado.httpStatus()).isNull();
        assertThat(servidor.pedidos()).isEmpty();
        assertThat(log.ultimaChamada("VALIDATE_USER").path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveManterOLoginDisponivelComOCircuitoDoEnvioAberto() {
        ClienteIncm cliente = criarCliente();
        registry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).transitionToOpenState();
        servidor.responder("/ValidateUser", 200, VALIDATE_USER_RECUSADO);

        ResultadoChamadaIncm<ValidateUserResponse> resultado = cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho)
                .containsExactly("/ValidateUser");
    }

    @Test
    void deveManterOEnvioDisponivelComOCircuitoDoLoginAberto() {
        ClienteIncm cliente = criarCliente();
        registry.circuitBreaker(ClienteIncm.CIRCUITO_AUTENTICACAO).transitionToOpenState();
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);

        ResultadoChamadaIncm<SubmitNewActResponse> resultado = cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho)
                .containsExactly("/SubmitNewAct");
    }

    @Test
    void deveDevolverErroRedeQuandoAIncmEstaInacessivel() {
        ClienteIncm cliente = criarCliente(ServidorIncmTeste.urlInacessivel(), Duration.ofSeconds(5));

        ResultadoChamadaIncm<SubmitNewActResponse> resultado = cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_REDE);
        assertThat(resultado.httpStatus()).isNull();
    }

    @Test
    void deveSubmeterAtoEConsultarDetalhesComOActIdentifierDevolvido() throws Exception {
        ClienteIncm cliente = criarCliente();
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);
        servidor.responder("/GetActDetails", 200, DETALHES_PUBLICADO);

        ResultadoChamadaIncm<SubmitNewActResponse> submissao = cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        long actIdentifier = submissao.corpo().actIdentifier();
        ResultadoChamadaIncm<GetActDetailsResponse> detalhes = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier);

        assertThat(submissao.httpStatus()).isEqualTo(200);
        assertThat(submissao.falhaTecnica()).isNull();
        assertThat(submissao.corpo().isSuccess()).isTrue();
        assertThat(actIdentifier).isEqualTo(ACT_IDENTIFIER);
        JsonNode pedidoDetalhes = objectMapper.readTree(servidor.pedidos().get(1).corpo());
        assertThat(pedidoDetalhes.get("ActIdentifier").asLong()).isEqualTo(ACT_IDENTIFIER);

        GetActDetailsResponse corpo = detalhes.corpo();
        assertThat(detalhes.httpStatus()).isEqualTo(200);
        assertThat(corpo.processingStatus()).isEqualTo("SubmissionAccepted");
        assertThat(corpo.actStatus()).isEqualTo("Publicado");
        assertThat(corpo.actReferenceIdentifier()).isEqualTo("DR-2026-000001");
        assertThat(corpo.createdOn()).isEqualTo(Instant.parse("2026-10-23T10:00:00Z"));
        assertThat(corpo.actLogList()).singleElement()
                .satisfies(registo -> assertThat(registo.actStatus()).isEqualTo("Em edição"));
        assertThat(corpo.publication().actUrl()).isEqualTo("https://exemplo.pt/dr/2026/000001");
        assertThat(corpo.publication().actPdfUrl()).isEqualTo("https://exemplo.pt/dr/2026/000001/ato.pdf");
        assertThat(corpo.publication().actPublicationDate()).isEqualTo(LocalDate.of(2026, 10, 24));
    }

    @Test
    void deveEnviarSoASerieDoAtoComAsDatasNoFormatoDoContrato() throws Exception {
        ClienteIncm cliente = criarCliente();
        servidor.responder("/SubmitNewAct", 200, SUBMIT_SUCESSO);
        SubmitNewActRequest serie1 = new SubmitNewActRequest(new SubmitNewActRequest.DgtAct("2015482",
                new SubmitNewActRequest.DiarioRepublicaSeries1Act(1234567891234567L, false, null, "Sumário",
                        LocalDate.of(2025, 12, 31)),
                null, List.of(), List.of()));

        cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        cliente.submeterAto(PROCESSO_ID, serie1);

        JsonNode dgtActSerie2 = objectMapper.readTree(servidor.pedidos().get(0).corpo()).get("DGTAct");
        assertThat(dgtActSerie2.has("DiarioRepublicaSeries1Act")).isFalse();
        assertThat(dgtActSerie2.get("UserEnrollmentAgreementIdentifier").asText()).isEqualTo("2015482");
        JsonNode ato2 = dgtActSerie2.get("DiarioRepublicaSeries2Act");
        assertThat(ato2.get("ActTypeId").asLong()).isEqualTo(1234567891234568L);
        assertThat(ato2.get("PartId").asLong()).isEqualTo(1234567891234567L);
        assertThat(ato2.get("InformationTypeIdentifier").asText()).isEqualTo("full-text");
        assertThat(ato2.get("SignatureDate").asText()).isEqualTo("2025-12-31");
        assertThat(ato2.get("LegalProvision").asText()).isEqualTo("Artigo 76.º do RJIGT");
        assertThat(dgtActSerie2.get("ImageURLList").isEmpty()).isTrue();
        assertThat(dgtActSerie2.get("FileURLList").get(0).asText()).isEqualTo("https://exemplo.pt/publico/token/aviso.pdf");

        JsonNode dgtActSerie1 = objectMapper.readTree(servidor.pedidos().get(1).corpo()).get("DGTAct");
        assertThat(dgtActSerie1.has("DiarioRepublicaSeries2Act")).isFalse();
        JsonNode ato1 = dgtActSerie1.get("DiarioRepublicaSeries1Act");
        assertThat(ato1.get("PublishInSuplement").asBoolean()).isFalse();
        assertThat(ato1.has("SuplementPublishingDate")).isFalse();
        assertThat(ato1.get("SignatureDate").asText()).isEqualTo("2025-12-31");
    }

    @Test
    void deveAbrirOCircuitoComRespostas5xxMasNaoComIsSuccessFalso() {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        ClienteIncm cliente = criarCliente();
        CircuitBreaker circuito = registry.circuitBreaker(ClienteIncm.CIRCUITO_AUTENTICACAO);
        servidor.responder("/ValidateUser", 200, VALIDATE_USER_RECUSADO);

        cliente.validarUtilizador("ninguem@cm-lisboa.pt", "errada");
        cliente.validarUtilizador("ninguem@cm-lisboa.pt", "errada");
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        servidor.responder("/ValidateUser", 500, null);
        cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");
        cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        ResultadoChamadaIncm<ValidateUserResponse> resultado = cliente.validarUtilizador("ana@cm-lisboa.pt", "senha");
        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.CIRCUITO_ABERTO);
        assertThat(servidor.pedidos()).hasSize(4);
    }

    @Test
    void deveAbrirSoOCircuitoDoEnvioQuandoOSubmitNewActFalha() {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        ClienteIncm cliente = criarCliente();
        servidor.responder("/SubmitNewAct", 500, null);
        servidor.responder("/ValidateUser", 200, VALIDATE_USER_RECUSADO);

        cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));
        cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso"));

        assertThat(registry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(registry.circuitBreaker(ClienteIncm.CIRCUITO_AUTENTICACAO).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cliente.submeterAto(PROCESSO_ID, pedidoSerie2("Aviso")).falhaTecnica())
                .isEqualTo(FalhaTecnicaIncm.CIRCUITO_ABERTO);
        assertThat(cliente.validarUtilizador("ana@cm-lisboa.pt", "senha").temFalhaTecnica()).isFalse();
    }
}
