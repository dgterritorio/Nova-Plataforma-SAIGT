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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LogJson.Extensao.class)
class ClienteSnitDepositoTest {

    private static final String CHAVE = "chave-snit-deposito-teste-4d7a";
    private static final String CAMINHO = "/api/integrations/saigt/deposits";
    private static final String CAMINHO_FICHEIROS = CAMINHO + "/88100/files";
    private static final String CAMINHO_DEPOSITO = CAMINHO + "/88100";
    private static final String IDUS_DINAMICA = "01.1199.01.000999.02.00005.20201226";
    private static final UUID PROCESSO_ID = UUID.fromString("5b2e7c1a-3f4d-4a8b-9c6e-0d1f2a3b4c5d");
    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(PROCESSO_ID, null, null);
    private static final String NUMERO_DEPOSITO = "03.11.11/PDM/02/2026/7";
    private static final String URL_PASTA = "https://saigt.exemplo.pt/api/v1/integracoes/snit/depositos/00042";

    private static final String RECEBIDO = """
            {"ssaigtId": 88100, "depositNumber": "03.11.11/PDM/02/2026/7", "availabilityTaskId": 14230,
             "existingTask": false, "processStatus": "pending_start", "phase": "process_registration",
             "idIgt": null, "activeDynamicsCount": null, "campoNovo": 1}
            """;
    private static final String RECEBIDO_REPETICAO = """
            {"ssaigtId": 88100, "depositNumber": "03.11.11/PDM/02/2026/7", "availabilityTaskId": 14230,
             "existingTask": true, "processStatus": "in_progress", "phase": "other", "idIgt": 1001,
             "activeDynamicsCount": 2}
            """;
    private static final String FICHEIROS_RECEBIDOS = """
            {"ssaigtId": 88100, "fileCount": 2}
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

    /** Espelha `instances.snit-registo.ignore-exceptions` das properties. */
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

    private static DepositoSnit depositoCompleto() {
        return new DepositoSnit(88100L, NUMERO_DEPOSITO, Instant.parse("2026-11-10T10:00:00Z"), "IDUS-0001",
                URL_PASTA, new VerificacaoSnit(Instant.parse("2026-11-10T09:00:00Z"), "utilizador.teste"));
    }

    private static DepositoSnit depositoSoComObrigatorios() {
        return new DepositoSnit(88100L, NUMERO_DEPOSITO, Instant.parse("2026-11-10T10:00:00Z"), null, null, null);
    }

    private static List<FicheiroDepositoSnit> doisFicheiros() {
        return List.of(
                new FicheiroDepositoSnit("8e1f0c2a-1111-4c3d-9e8f-000000000001", "PO_Sintra", "PO_Sintra.zip",
                        URL_PASTA + "/ficheiros/8e1f0c2a-1111-4c3d-9e8f-000000000001", true,
                        Instant.parse("2026-11-10T08:00:00Z")),
                new FicheiroDepositoSnit("8e1f0c2a-2222-4c3d-9e8f-000000000002", "PC_Sintra", null,
                        URL_PASTA + "/ficheiros/8e1f0c2a-2222-4c3d-9e8f-000000000002", null,
                        Instant.parse("2026-11-10T08:05:00Z")));
    }

    @Test
    void deveRegistarODepositoComAChaveEOsNomesDoContratoQuandoOSnitResponde201() throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDO);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(depositoCompleto(), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo()).isEqualTo(new DepositoRecebidoSnit(88100L, NUMERO_DEPOSITO, 14230L, false,
                "pending_start", "process_registration", null, null));
        ServidorIncmTeste.PedidoRecebido pedido = servidor.pedidos().getFirst();
        assertThat(pedido.caminho()).isEqualTo(CAMINHO);
        assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        assertThat(pedido.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        JsonNode corpo = objectMapper.readTree(pedido.corpo());
        assertThat(corpo.get("ssaigtId").asLong()).isEqualTo(88100L);
        assertThat(corpo.get("depositNumber").asText()).isEqualTo(NUMERO_DEPOSITO);
        assertThat(corpo.get("depositedAt").asText()).isEqualTo("2026-11-10T10:00:00Z");
        assertThat(corpo.get("idus").asText()).isEqualTo("IDUS-0001");
        assertThat(corpo.get("downloadUrl").asText()).isEqualTo(URL_PASTA);
        assertThat(corpo.get("verification").get("verifiedAt").asText()).isEqualTo("2026-11-10T09:00:00Z");
        assertThat(corpo.get("verification").get("verifiedBy").asText()).isEqualTo("utilizador.teste");
    }

    @Test
    void deveOmitirOsCamposOpcionaisSemValorNoDeposito() throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDO);

        criarCliente().registarDeposito(depositoSoComObrigatorios(), REFERENCIA);

        String json = servidor.pedidos().getFirst().corpo();
        JsonNode corpo = objectMapper.readTree(json);
        assertThat(json).doesNotContain("null");
        assertThat(corpo.has("idus")).isFalse();
        assertThat(corpo.has("downloadUrl")).isFalse();
        assertThat(corpo.has("verification")).isFalse();
        assertThat(corpo.size()).isEqualTo(3);
    }

    @Test
    void deveDevolverOCorpoSemFalhaNaRepeticaoIdempotenteQuandoOSnitResponde200() {
        servidor.responder(CAMINHO, 200, RECEBIDO_REPETICAO);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(depositoCompleto(), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().existingTask()).isTrue();
        assertThat(resultado.corpo().idIgt()).isEqualTo(1001L);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 409})
    void deveDevolverErroHttpSemContarParaOCircuitoNoDepositoRecusado(int codigo) {
        servidor.responder(CAMINHO, codigo, null);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(depositoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(resultado.corpo()).isNull();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void deveContarO5xxDoDepositoParaOCircuitoESemNovaTentativa(int codigo) {
        servidor.responder(CAMINHO, codigo, null);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(depositoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveContarOTimeoutDoDepositoParaOCircuitoESemNovaTentativa() {
        servidor.reterResposta(CAMINHO);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente(servidor.url(), Duration.ofMillis(300)).registarDeposito(depositoCompleto(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveRegistarOsFicheirosComOSsaigtIdNoCaminhoEOsFilesNumObjeto() throws Exception {
        servidor.responder(CAMINHO_FICHEIROS, 201, FICHEIROS_RECEBIDOS);

        ResultadoChamadaExterna<FicheirosRecebidosSnit> resultado =
                criarCliente().registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo()).isEqualTo(new FicheirosRecebidosSnit(88100L, 2));
        ServidorIncmTeste.PedidoRecebido pedido = servidor.pedidos().getFirst();
        assertThat(pedido.caminho()).isEqualTo(CAMINHO_FICHEIROS);
        assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        JsonNode corpo = objectMapper.readTree(pedido.corpo());
        assertThat(corpo.isObject()).isTrue();
        assertThat(corpo.get("files")).hasSize(2);
        JsonNode primeiro = corpo.get("files").get(0);
        assertThat(primeiro.get("fileId").asText()).isEqualTo("8e1f0c2a-1111-4c3d-9e8f-000000000001");
        assertThat(primeiro.get("fileName").asText()).isEqualTo("PO_Sintra");
        assertThat(primeiro.get("downloadName").asText()).isEqualTo("PO_Sintra.zip");
        assertThat(primeiro.get("downloadUrl").asText())
                .isEqualTo(URL_PASTA + "/ficheiros/8e1f0c2a-1111-4c3d-9e8f-000000000001");
        assertThat(primeiro.get("isGraphicPiece").asBoolean()).isTrue();
        assertThat(primeiro.get("createdAt").asText()).isEqualTo("2026-11-10T08:00:00Z");
        JsonNode segundo = corpo.get("files").get(1);
        assertThat(segundo.has("downloadName")).isFalse();
        assertThat(segundo.has("isGraphicPiece")).isFalse();
        assertThat(pedido.corpo()).doesNotContain("sheetNumber").doesNotContain("null");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404})
    void deveDevolverErroHttpSemContarParaOCircuitoNosFicheirosRecusados(int codigo) {
        servidor.responder(CAMINHO_FICHEIROS, codigo, null);

        ResultadoChamadaExterna<FicheirosRecebidosSnit> resultado =
                criarCliente().registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void deveContarO5xxDosFicheirosParaOCircuitoESemNovaTentativa() {
        servidor.responder(CAMINHO_FICHEIROS, 500, null);

        ResultadoChamadaExterna<FicheirosRecebidosSnit> resultado =
                criarCliente().registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveDevolverCircuitoAbertoSemPedidoNasDuasOperacoes() {
        ClienteSnit cliente = criarCliente();
        circuitoRegisto().transitionToOpenState();

        ResultadoChamadaExterna<DepositoRecebidoSnit> deposito = cliente.registarDeposito(depositoCompleto(), REFERENCIA);
        ResultadoChamadaExterna<FicheirosRecebidosSnit> ficheiros =
                cliente.registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA);

        assertThat(deposito.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(ficheiros.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.CIRCUITO_ABERTO);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoNasDuasOperacoesQuandoOEnderecoEstaVazio() {
        ClienteSnit cliente = criarCliente("", Duration.ofSeconds(5));

        ResultadoChamadaExterna<DepositoRecebidoSnit> deposito = cliente.registarDeposito(depositoCompleto(), REFERENCIA);
        ResultadoChamadaExterna<FicheirosRecebidosSnit> ficheiros =
                cliente.registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA);

        assertThat(deposito.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(ficheiros.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void deveEscreverAsLinhasDeIntegracaoSemCorposNemChave(LogJson log) throws Exception {
        servidor.responder(CAMINHO, 201, RECEBIDO);
        servidor.responder(CAMINHO_FICHEIROS, 201, FICHEIROS_RECEBIDOS);
        ClienteSnit cliente = criarCliente();
        MockHttpServletRequest pedidoHttp = new MockHttpServletRequest("POST", "/api/v1/processos");
        pedidoHttp.addHeader("X-Trace-Id", "trace-snit-244");
        AtomicReference<ResultadoChamadaExterna<FicheirosRecebidosSnit>> resultado = new AtomicReference<>();

        new TraceIdFilter().doFilter(pedidoHttp, new MockHttpServletResponse(), (pedido, resposta) -> {
            cliente.registarDeposito(depositoCompleto(), REFERENCIA);
            resultado.set(cliente.registarFicheirosDeposito(88100L, doisFicheiros(), REFERENCIA));
        });

        assertThat(resultado.get().temFalhaTecnica()).isFalse();
        assertThat(log.chamadas("REGISTAR_DEPOSITO")).hasSize(1);
        JsonNode ficheiros = log.ultimaChamada("REGISTAR_FICHEIROS_DEPOSITO");
        assertThat(ficheiros.path("url").asText()).isEqualTo(servidor.url() + CAMINHO_FICHEIROS);
        assertThat(ficheiros.path("correlationId").asText()).isEqualTo("trace-snit-244");
        assertThat(ficheiros.path("processoId").asText()).isEqualTo(PROCESSO_ID.toString());
        assertThat(ficheiros.path("cabecalhosPedido").path("X-Saigt-Api-Key").asText()).isEqualTo("***");
        assertThat(log.todas()).doesNotContain(CHAVE).doesNotContain(NUMERO_DEPOSITO).doesNotContain(URL_PASTA)
                .doesNotContain("PO_Sintra").doesNotContain("14230");
    }

    @Test
    void deveEnviarSoOIdusDaDinamicaNoPutDoDepositoComAChave() throws Exception {
        servidor.responder(CAMINHO_DEPOSITO, 200, RECEBIDO_REPETICAO);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().processStatus()).isEqualTo("in_progress");
        ServidorIncmTeste.PedidoRecebido pedido = servidor.pedidos().getFirst();
        assertThat(pedido.metodo()).isEqualTo("PUT");
        assertThat(pedido.caminho()).isEqualTo(CAMINHO_DEPOSITO);
        assertThat(pedido.cabecalhos().getFirst("X-Saigt-Api-Key")).isEqualTo(CHAVE);
        JsonNode corpo = objectMapper.readTree(pedido.corpo());
        assertThat(corpo.size()).isEqualTo(1);
        assertThat(corpo.get("idus").asText()).isEqualTo(IDUS_DINAMICA);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 409})
    void deveDevolverErroHttpSemContarParaOCircuitoNoPutDoDepositoRecusado(int codigo) {
        servidor.responder(CAMINHO_DEPOSITO, codigo, null);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(codigo);
        assertThat(circuitoRegisto().getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveContarO5xxDoPutDoDepositoParaOCircuitoESemNovaTentativa() {
        servidor.responder(CAMINHO_DEPOSITO, 503, null);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveContarOTimeoutDoPutDoDepositoParaOCircuitoESemNovaTentativa() {
        servidor.reterResposta(CAMINHO_DEPOSITO);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado = criarCliente(servidor.url(), Duration.ofMillis(300))
                .atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(circuitoRegisto().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(servidor.pedidos()).hasSize(1);
    }

    @Test
    void deveEscreverALinhaDoPutDoDepositoSemCorpoNemChave(LogJson log) {
        servidor.responder(CAMINHO_DEPOSITO, 200, RECEBIDO_REPETICAO);

        criarCliente().atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        JsonNode evento = log.ultimaChamada("ATUALIZAR_IDUS_DEPOSITO");
        assertThat(evento.path("url").asText()).isEqualTo(servidor.url() + CAMINHO_DEPOSITO);
        assertThat(evento.path("cabecalhosPedido").path("X-Saigt-Api-Key").asText()).isEqualTo("***");
        assertThat(log.todas()).doesNotContain(CHAVE).doesNotContain(NUMERO_DEPOSITO).doesNotContain("in_progress");
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoNoPutDoDepositoQuandoOEnderecoEstaVazio() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente("", Duration.ofSeconds(5)).atualizarIdusDeposito(88100L, IDUS_DINAMICA, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
    }
}
