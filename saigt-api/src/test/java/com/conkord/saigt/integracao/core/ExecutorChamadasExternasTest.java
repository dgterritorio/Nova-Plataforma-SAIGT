package com.conkord.saigt.integracao.core;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.conkord.saigt.integracao.pcgt.ClientePcgt;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.lang.reflect.RecordComponent;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(LogJson.Extensao.class)
class ExecutorChamadasExternasTest {

    private static final String CHAVE = "chave-externa-teste-91c2";
    private static final String CIRCUITO = "sistema-teste";
    private static final String CIRCUITO_INCM = ClienteIncm.CIRCUITO_SUBMISSAO;
    private static final String OPERACAO = "OPERACAO_TESTE";
    private static final String CAMINHO = "/recursos";
    private static final UUID PROCESSO_ID = UUID.fromString("7d1e4b2a-9c3f-4a6e-8b5d-2f0c1a9e3b64");
    private static final String MARCA_CORPO = "corpo-nao-registar-5e1";

    record Recurso(String valor) {
    }

    private ServidorIncmTeste servidor;
    private CircuitBreakerRegistry registry;
    private ExecutorChamadasExternas executor;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        registry = CircuitBreakerRegistry.ofDefaults();
        executor = new ExecutorChamadasExternas(registry, evento -> { });
    }

    @AfterEach
    void parar() {
        servidor.close();
        MDC.remove("apiTraceId");
    }

    private static RestClient criarRestClient(String baseUrl, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build());
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor())
                .defaultHeader(SistemaExterno.SNIT.cabecalhoCredencial(), CHAVE)
                .build();
    }

    private ResultadoChamadaExterna<Recurso> chamar(String baseUrl, Duration readTimeout) {
        RestClient restClient = criarRestClient(baseUrl, readTimeout);
        return executor.executar(SistemaExterno.SNIT, OPERACAO, CIRCUITO, "GET",
                baseUrl + CAMINHO + "?municipality=1111", new ReferenciaChamada(PROCESSO_ID, "IDUS-TESTE", null),
                () -> restClient.get().uri(CAMINHO + "?municipality=1111").retrieve().toEntity(Recurso.class));
    }

    private ResultadoChamadaExterna<Recurso> chamar() {
        return chamar(servidor.url(), Duration.ofSeconds(5));
    }

    private static JsonNode eventoChamada(LogJson log) {
        return log.ultimaChamada(OPERACAO);
    }

    /** O cliente HTTP do JDK entrega os nomes dos cabeçalhos de resposta em minúsculas. */
    private static String cabecalho(JsonNode cabecalhos, String nome) {
        return cabecalhos.properties().stream().filter(campo -> campo.getKey().equalsIgnoreCase(nome))
                .map(campo -> campo.getValue().get(0).asText()).findFirst()
                .orElseThrow(() -> new AssertionError("Sem o cabeçalho " + nome + " em " + cabecalhos));
    }

    static List<JsonNode> transicoes(LogJson log, String circuito) {
        return log.doLogger(ExecutorChamadasExternas.class).stream()
                .filter(evento -> circuito.equals(evento.path("circuito").asText())).toList();
    }

    @Test
    void deveDevolverCorpoEEscreverEventoInfoComOsCamposDaChamada(LogJson log) {
        servidor.responder(CAMINHO, 200, "{\"valor\": \"ok\"}");
        MDC.put("apiTraceId", "trace-externo-157");
        Instant antes = Instant.now();

        ResultadoChamadaExterna<Recurso> resultado = chamar();

        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo()).isEqualTo(new Recurso("ok"));
        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.duracaoMs()).isNotNegative();
        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Trace-Id")).isEqualTo("trace-externo-157");
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("INFO");
        assertThat(evento.path("logger").asText()).isEqualTo(ExecutorChamadasExternas.class.getName());
        assertThat(evento.path("sistema").asText()).isEqualTo("SNIT");
        assertThat(evento.path("operacao").asText()).isEqualTo(OPERACAO);
        assertThat(evento.path("processoId").asText()).isEqualTo(PROCESSO_ID.toString());
        assertThat(evento.path("idus").asText()).isEqualTo("IDUS-TESTE");
        assertThat(evento.has("actIdentifier")).isFalse();
        assertThat(evento.path("metodo").asText()).isEqualTo("GET");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + CAMINHO);
        assertThat(evento.path("estadoHttp").isInt()).isTrue();
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(200);
        assertThat(evento.path("duracaoMs").isIntegralNumber()).isTrue();
        assertThat(evento.path("duracaoMs").asLong()).isNotNegative();
        assertThat(evento.path("resultado").asText()).isEqualTo("OK");
        assertThat(Instant.parse(evento.path("enviadoEm").asText())).isBetween(antes, Instant.now());
        assertThat(evento.path("correlationId").asText()).isEqualTo("trace-externo-157");
        assertThat(log.todas()).doesNotContain("municipality");
    }

    @Test
    void deveDevolverErroHttpComOCodigoSemExcecaoQuandoOSistemaResponde5xx(LogJson log) {
        servidor.responder(CAMINHO, 503, "{\"detalhe\": \"" + MARCA_CORPO + "\"}");
        AtomicReference<ResultadoChamadaExterna<Recurso>> resultado = new AtomicReference<>();

        assertThatCode(() -> resultado.set(chamar())).doesNotThrowAnyException();

        assertThat(resultado.get().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.get().httpStatus()).isEqualTo(503);
        assertThat(resultado.get().corpo()).isNull();
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("ERROR");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(503);
        assertThat(evento.path("resultado").asText()).isEqualTo("ERRO_HTTP");
        assertThat(log.todas()).doesNotContain(MARCA_CORPO);
    }

    @Test
    void deveDevolverTimeoutQuandoOSistemaNaoResponde(LogJson log) {
        servidor.reterResposta(CAMINHO);

        ResultadoChamadaExterna<Recurso> resultado = chamar(servidor.url(), Duration.ofMillis(300));

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(resultado.httpStatus()).isNull();
        assertThat(resultado.duracaoMs()).isBetween(250L, 5000L);
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("ERROR");
        assertThat(evento.path("estadoHttp").asInt()).isZero();
        assertThat(evento.path("resultado").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void deveDevolverErroRedeQuandoALigacaoERecusada(LogJson log) {
        ResultadoChamadaExterna<Recurso> resultado = chamar(ServidorIncmTeste.urlInacessivel(), Duration.ofSeconds(5));

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_REDE);
        assertThat(resultado.httpStatus()).isNull();
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("ERROR");
        assertThat(evento.path("estadoHttp").asInt()).isZero();
        assertThat(evento.path("resultado").asText()).isEqualTo("ERRO_REDE");
    }

    @Test
    void deveDevolverErroHttpSemRegistarOCorpoQuandoUmaResposta2xxNaoSeLe(LogJson log) {
        servidor.responder(CAMINHO, 200, "isto não é JSON " + MARCA_CORPO);

        ResultadoChamadaExterna<Recurso> resultado = chamar();

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.corpo()).isNull();
        assertThat(eventoChamada(log).path("level").asText()).isEqualTo("ERROR");
        assertThat(eventoChamada(log).path("resultado").asText()).isEqualTo("ERRO_HTTP");
        assertThat(log.todas()).doesNotContain(MARCA_CORPO);
    }

    @Test
    void deveDevolverCircuitoAbertoSemChamarOSistema(LogJson log) {
        registry.circuitBreaker(CIRCUITO).transitionToOpenState();

        ResultadoChamadaExterna<Recurso> resultado = chamar();

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(resultado.httpStatus()).isNull();
        assertThat(servidor.pedidos()).isEmpty();
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("WARN");
        assertThat(evento.path("estadoHttp").asInt()).isZero();
        assertThat(evento.path("resultado").asText()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveMascararACredencialESemCorposNemQueryStringNoEventoDaChamada(LogJson log) {
        servidor.responder(CAMINHO, 200, "{\"valor\": \"" + MARCA_CORPO + "\"}");

        chamar();

        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("cabecalhosPedido").path("X-Saigt-Api-Key").asText()).isEqualTo("***");
        assertThat(log.todas()).doesNotContain(CHAVE).doesNotContain(MARCA_CORPO).doesNotContain("municipality=1111");
    }

    @Test
    void deveMascararOsCabecalhosDeRespostaSensiveisETirarCredenciaisDoUrl(LogJson log) {
        servidor.responder(CAMINHO, 200, "{\"valor\": \"ok\"}", Map.of(
                "Set-Cookie", "sessao=" + MARCA_CORPO, "Authorization", "Bearer " + MARCA_CORPO,
                "Proxy-Authorization", "Basic " + MARCA_CORPO, "X-Pedido", "visivel-3a9"));
        RestClient restClient = criarRestClient(servidor.url(), Duration.ofSeconds(5));
        String urlComCredenciais = servidor.url().replace("://", "://utilizador:" + CHAVE + "@") + CAMINHO
                + "?token=" + CHAVE + "#fragmento";

        executor.executar(SistemaExterno.SNIT, OPERACAO, CIRCUITO, "GET", urlComCredenciais,
                new ReferenciaChamada(null, null, 4711L),
                () -> restClient.get().uri(CAMINHO).retrieve().toEntity(Recurso.class));

        JsonNode evento = eventoChamada(log);
        JsonNode cabecalhos = evento.path("cabecalhosResposta");
        assertThat(cabecalho(cabecalhos, "Set-Cookie")).isEqualTo("***");
        assertThat(cabecalho(cabecalhos, "Authorization")).isEqualTo("***");
        assertThat(cabecalho(cabecalhos, "Proxy-Authorization")).isEqualTo("***");
        assertThat(cabecalho(cabecalhos, "X-Pedido")).isEqualTo("visivel-3a9");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + CAMINHO);
        assertThat(evento.path("actIdentifier").asLong()).isEqualTo(4711L);
        assertThat(evento.has("processoId")).isFalse();
        assertThat(evento.has("idus")).isFalse();
        assertThat(log.todas()).doesNotContain(MARCA_CORPO).doesNotContain(CHAVE).doesNotContain("fragmento");
    }

    private ResultadoChamadaExterna<Recurso> chamarRegistandoResposta() {
        RestClient restClient = criarRestClient(servidor.url(), Duration.ofSeconds(5));
        return executor.executar(SistemaExterno.SNIT, OPERACAO, CIRCUITO, "GET", servidor.url() + CAMINHO,
                ReferenciaChamada.NENHUMA, true,
                () -> restClient.get().uri(CAMINHO).retrieve().toEntity(Recurso.class));
    }

    @Test
    void deveRegistarOCorpoDaRespostaQuandoAOperacaoOPede(LogJson log) {
        servidor.responder(CAMINHO, 200, "{\"valor\": \"" + MARCA_CORPO + "\"}");

        chamarRegistandoResposta();

        assertThat(eventoChamada(log).path("resposta").asText()).isEqualTo("{\"valor\":\"" + MARCA_CORPO + "\"}");
    }

    @Test
    void deveRegistarOCorpoDeUmErroHttpEmJsonCompactoQuandoAOperacaoOPede(LogJson log) {
        servidor.responder(CAMINHO, 503, "{\n  \"detalhe\": \"" + MARCA_CORPO + "\"\n}");

        chamarRegistandoResposta();

        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("resposta").asText()).isEqualTo("{\"detalhe\":\"" + MARCA_CORPO + "\"}");
        assertThat(evento.path("resultado").asText()).isEqualTo("ERRO_HTTP");
    }

    @Test
    void deveManterNumaLinhaUmCorpoDeErroQueNaoEJson(LogJson log) {
        servidor.responder(CAMINHO, 502, "Bad Gateway\r\n" + MARCA_CORPO);

        chamarRegistandoResposta();

        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("resposta").asText()).isEqualTo("Bad Gateway\\r\\n" + MARCA_CORPO);
        assertThat(evento.path("resultado").asText()).isEqualTo("ERRO_HTTP");
    }

    @Test
    void deveCortarUmCorpoDeRespostaAcimaDoLimite(LogJson log) {
        String valorLongo = "x".repeat(ExecutorChamadasExternas.MAXIMO_CARACTERES_RESPOSTA);
        servidor.responder(CAMINHO, 200, "{\"valor\": \"" + valorLongo + "\"}");

        chamarRegistandoResposta();

        int tamanhoJson = "{\"valor\":\"\"}".length() + valorLongo.length();
        assertThat(eventoChamada(log).path("resposta").asText())
                .isEqualTo("{\"valor\":\"" + "x".repeat(ExecutorChamadasExternas.MAXIMO_CARACTERES_RESPOSTA - 10)
                        + "...[TRUNCADO " + tamanhoJson + " caracteres]");
        assertThat(log.todas()).doesNotContain(valorLongo);
    }

    @Test
    void deveContarAsFalhasNoCircuitoDoSistemaEAbriLo() {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        executor = new ExecutorChamadasExternas(registry, evento -> { });
        servidor.responder(CAMINHO, 500, null);

        chamar();
        chamar();

        assertThat(registry.circuitBreaker(CIRCUITO).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(chamar().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(servidor.pedidos()).hasSize(2);
    }

    @Test
    void deveTratarComoSucessoSemCorpoUmaRespostaNao2xxDevolvidaPelaChamada(LogJson log) {
        ResultadoChamadaExterna<Recurso> resultado = executor.executar(SistemaExterno.SNIT, OPERACAO, CIRCUITO, "GET",
                servidor.url() + CAMINHO, ReferenciaChamada.NENHUMA,
                () -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Recurso("ignorado")));

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(404);
        assertThat(resultado.corpo()).isNull();
        assertThat(registry.circuitBreaker(CIRCUITO).getMetrics().getNumberOfFailedCalls()).isZero();
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("INFO");
        assertThat(evento.path("estadoHttp").asInt()).isEqualTo(404);
        assertThat(evento.has("processoId")).isFalse();
        assertThat(evento.has("idus")).isFalse();
        assertThat(evento.has("correlationId")).isFalse();
    }

    @Test
    void deveRegistarSemChamarUmSistemaNaoConfigurado(LogJson log) {
        ResultadoChamadaExterna<Recurso> resultado =
                executor.naoConfigurado(SistemaExterno.PCGT, OPERACAO, "POST", "/login", ReferenciaChamada.NENHUMA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(resultado.httpStatus()).isNull();
        JsonNode evento = eventoChamada(log);
        assertThat(evento.path("level").asText()).isEqualTo("ERROR");
        assertThat(evento.path("sistema").asText()).isEqualTo("PCGT");
        assertThat(evento.path("cabecalhosPedido").path("Authorization").asText()).isEqualTo("***");
        assertThat(evento.path("estadoHttp").asInt()).isZero();
        assertThat(evento.path("resultado").asText()).isEqualTo("NAO_CONFIGURADO");
    }

    @Test
    void deveEscreverUmEventoWarnPorTransicaoDosCircuitosExistentesEDosNovos(LogJson log) {
        CircuitBreakerRegistry registryComCircuito = CircuitBreakerRegistry.ofDefaults();
        registryComCircuito.circuitBreaker("pcgt");
        new ExecutorChamadasExternas(registryComCircuito, evento -> { });

        registryComCircuito.circuitBreaker("pcgt").transitionToOpenState();
        registryComCircuito.circuitBreaker("geoconform").transitionToOpenState();

        assertThat(transicoes(log, "pcgt")).singleElement().satisfies(evento -> {
            assertThat(evento.path("level").asText()).isEqualTo("WARN");
            assertThat(evento.path("estadoOrigem").asText()).isEqualTo("CLOSED");
            assertThat(evento.path("estadoDestino").asText()).isEqualTo("OPEN");
        });
        assertThat(transicoes(log, "geoconform")).singleElement()
                .satisfies(evento -> assertThat(evento.path("estadoDestino").asText()).isEqualTo("OPEN"));
    }

    @Test
    void deveLevarOCorrelationIdDoPedidoNoCabecalhoDeSaidaENoEvento(LogJson log) {
        servidor.responder(CAMINHO, 200, "{\"valor\": \"ok\"}");
        MDC.put("apiTraceId", "abc-123");

        chamar();

        assertThat(servidor.pedidos().getFirst().cabecalhos().getFirst("X-Trace-Id")).isEqualTo("abc-123");
        assertThat(eventoChamada(log).path("correlationId").asText()).isEqualTo("abc-123");
    }

    private ExecutorChamadasExternas executorComCircuitoQueAbreAoSegundoErro(ApplicationEventPublisher publicador) {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
        return new ExecutorChamadasExternas(registry, publicador);
    }

    private ResultadoChamadaExterna<Recurso> chamarIncmSubmissao() {
        RestClient restClient = criarRestClient(servidor.url(), Duration.ofSeconds(5));
        return executor.executar(SistemaExterno.INCM, OPERACAO, CIRCUITO_INCM, "POST", servidor.url() + CAMINHO,
                ReferenciaChamada.NENHUMA, () -> restClient.post().uri(CAMINHO).retrieve().toEntity(Recurso.class));
    }

    @Test
    void devePublicarUmSoCircuitoAbertoQuandoAsFalhasAbremOCircuitoENaoNaReaberturaDepoisDoHalfOpen(LogJson log) {
        List<Object> publicados = new ArrayList<>();
        executor = executorComCircuitoQueAbreAoSegundoErro(publicados::add);
        servidor.responder(CAMINHO, 503, null);
        Instant antes = Instant.now();

        chamarIncmSubmissao();
        chamarIncmSubmissao();

        assertThat(registry.circuitBreaker(CIRCUITO_INCM).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(publicados).singleElement().isInstanceOfSatisfying(CircuitoAbertoEvento.class, evento -> {
            assertThat(evento.sistema()).isEqualTo(SistemaExterno.INCM);
            assertThat(evento.circuito()).isEqualTo(CIRCUITO_INCM);
            assertThat(evento.momento()).isBetween(antes, Instant.now());
        });

        registry.circuitBreaker(CIRCUITO_INCM).transitionToHalfOpenState();
        chamarIncmSubmissao();

        assertThat(registry.circuitBreaker(CIRCUITO_INCM).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(publicados).hasSize(1);
        assertThat(transicoes(log, CIRCUITO_INCM).stream()
                .filter(evento -> "HALF_OPEN".equals(evento.path("estadoOrigem").asText())
                        && "OPEN".equals(evento.path("estadoDestino").asText())))
                .singleElement().satisfies(evento -> assertThat(evento.path("level").asText()).isEqualTo("WARN"));
    }

    @Test
    void naoPublicaCircuitoAbertoParaUmCircuitoForaDoMapaDeSistemas(LogJson log) {
        List<Object> publicados = new ArrayList<>();
        executor = new ExecutorChamadasExternas(registry, publicados::add);

        registry.circuitBreaker(CIRCUITO).transitionToOpenState();

        assertThat(publicados).isEmpty();
        // O executor do @BeforeEach escuta o mesmo registry: cada um escreve a sua transição.
        assertThat(transicoes(log, CIRCUITO)).isNotEmpty().allSatisfy(evento -> {
            assertThat(evento.path("estadoOrigem").asText()).isEqualTo("CLOSED");
            assertThat(evento.path("estadoDestino").asText()).isEqualTo("OPEN");
        });
    }

    static Stream<Arguments> circuitosDosClientes() {
        return Stream.of(
                Arguments.of(ClienteIncm.CIRCUITO_AUTENTICACAO, SistemaExterno.INCM),
                Arguments.of(ClienteIncm.CIRCUITO_SUBMISSAO, SistemaExterno.INCM),
                Arguments.of(ClienteSnit.CIRCUITO_CONSULTA, SistemaExterno.SNIT),
                Arguments.of(ClienteSnit.CIRCUITO_REGISTO, SistemaExterno.SNIT),
                Arguments.of(ClientePcgt.CIRCUITO, SistemaExterno.PCGT),
                Arguments.of("geoconform", SistemaExterno.GEOCONFORM));
    }

    @ParameterizedTest
    @MethodSource("circuitosDosClientes")
    void devePublicarOSistemaCertoParaCadaCircuitoDosClientes(String circuito, SistemaExterno sistema) {
        List<Object> publicados = new ArrayList<>();
        new ExecutorChamadasExternas(registry, publicados::add);

        registry.circuitBreaker(circuito).transitionToOpenState();

        assertThat(publicados).singleElement().isInstanceOfSatisfying(CircuitoAbertoEvento.class, evento -> {
            assertThat(evento.sistema()).isEqualTo(sistema);
            assertThat(evento.circuito()).isEqualTo(circuito);
        });
    }

    @Test
    void umaFalhaAPublicarNaoMudaOResultadoDaChamadaEDeixaUmErroComSistemaECircuito(LogJson log) {
        executor = executorComCircuitoQueAbreAoSegundoErro(evento -> {
            throw new IllegalStateException("listener em baixo");
        });
        servidor.responder(CAMINHO, 503, null);

        ResultadoChamadaExterna<Recurso> primeira = chamarIncmSubmissao();
        ResultadoChamadaExterna<Recurso> queAbre = chamarIncmSubmissao();
        ResultadoChamadaExterna<Recurso> comCircuitoAberto = chamarIncmSubmissao();

        assertThat(queAbre.falhaTecnica()).isEqualTo(primeira.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(queAbre.httpStatus()).isEqualTo(503);
        assertThat(comCircuitoAberto.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(log.doLogger(ExecutorChamadasExternas.class).stream()
                .filter(evento -> evento.path("message").asText().contains("Falha a publicar a abertura do circuito")))
                .singleElement().satisfies(evento -> {
                    assertThat(evento.path("level").asText()).isEqualTo("ERROR");
                    assertThat(evento.path("message").asText()).contains("sistema=INCM", "circuito=" + CIRCUITO_INCM);
                });
    }

    @Test
    void oCircuitoAbertoEventoSoTemSistemaCircuitoEMomento() {
        assertThat(Arrays.stream(CircuitoAbertoEvento.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("sistema", "circuito", "momento");
    }
}
