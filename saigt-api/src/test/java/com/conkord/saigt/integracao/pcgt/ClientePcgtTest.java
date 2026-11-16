package com.conkord.saigt.integracao.pcgt;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(LogJson.Extensao.class)
class ClientePcgtTest {

    private static final String UTILIZADOR = "conta-integracao-teste";
    private static final String SENHA = "senha-pcgt-teste-7f3a9c";
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.jwt-teste-1.assinatura";
    private static final String JWT_NOVO = "eyJhbGciOiJIUzI1NiJ9.jwt-teste-2.assinatura";
    private static final String LOGIN = "/api/auth/login";
    private static final String PROCESSOS = "/api/processos";
    private static final UUID PROCESSO_ID = UUID.fromString("3b9e0c4a-2f1d-4c8e-9a6b-5d7e8f9a0b1c");
    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(PROCESSO_ID, null, null);
    private static final String IDUS = "01.1111.01.000123.02.00042.01.031.01.20261024";
    private static final String PROCESSO = """
            {"token": "a3f1c2d4-0000-4000-8000-000000000042", "codigoProcesso": "00042", "numeroProcesso": "00042",
             "idSSAIGT": "00042", "designacao": "Alteração — PDM de Exemplo", "codigoIdus": "%s",
             "codigoIdusDecomposto": {"dico": "1111"}, "codigosIdusEtapas": [], "municipioToken": "1111",
             "tipoInstrumentoId": 2, "etapaAtualOrdem": 1, "estado": "ATIVO", "tipoAto": "Aviso",
             "dataAto": "2026-10-24", "campoInterno": {"x": 1}}
            """.formatted(IDUS);
    private static final String NAO_EXISTE = """
            {"code": 404, "message": "Processo não encontrado para o codigoProcesso: 00077"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServidorIncmTeste servidor;
    private CircuitBreakerRegistry circuitos;
    private RateLimiterRegistry limites;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        // Espelha instances.pcgt.ignore-exceptions das properties.
        circuitos = CircuitBreakerRegistry.ofDefaults();
        circuitos.circuitBreaker(ClientePcgt.CIRCUITO,
                CircuitBreakerConfig.custom().ignoreExceptions(HttpClientErrorException.class).build());
        limites = RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(300)
                .limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build());
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private ClientePcgt criarCliente(String baseUrl, Duration readTimeout) {
        PcgtProperties propriedades = new PcgtProperties(baseUrl, UTILIZADOR, SENHA, Duration.ofSeconds(2), readTimeout);
        return new ClientePcgt(RestClient.builder(), new ExecutorChamadasExternas(circuitos, evento -> { }), limites,
                propriedades);
    }

    private ClientePcgt criarCliente() {
        return criarCliente(servidor.url(), Duration.ofSeconds(5));
    }

    private CircuitBreaker circuito() {
        return circuitos.circuitBreaker(ClientePcgt.CIRCUITO);
    }

    private void loginAceite() {
        servidor.responder(LOGIN, 200, "{\"token\": \"" + JWT + "\"}");
    }

    private static PedidoAtualizacaoProcessoPcgt atualizacao(String codigo) {
        return new PedidoAtualizacaoProcessoPcgt(codigo, IDUS, "1111", "Aviso", "1234/2026", LocalDate.of(2026, 10, 24),
                "https://dre.exemplo.pt/aviso", null, null, null, null);
    }

    private static PedidoCriacaoProcessoPcgt criacao(String codigo) {
        return new PedidoCriacaoProcessoPcgt(codigo, null, null, "Alteração — PDM de Exemplo", "01", "02", "1111", IDUS,
                new EntidadePcgt("CM-EXEMPLO"), "Aviso", "1234/2026", LocalDate.of(2026, 10, 24),
                "https://dre.exemplo.pt/aviso", null, null, null, null);
    }

    private List<ServidorIncmTeste.PedidoRecebido> pedidosA(String caminho) {
        return servidor.pedidos().stream().filter(p -> p.caminho().equals(caminho)).toList();
    }

    @Test
    void deveFazerLoginComAsCredenciaisAntesDoPrimeiroPutEEnviarOBearer() throws Exception {
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().codigoProcesso()).isEqualTo("00042");
        assertThat(resultado.corpo().codigoIdus()).isEqualTo(IDUS);
        assertThat(resultado.corpo().tipoInstrumentoId()).isEqualTo("2");
        assertThat(resultado.corpo().dataAto()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho).containsExactly(LOGIN, PROCESSOS);
        JsonNode login = objectMapper.readTree(servidor.pedidos().getFirst().corpo());
        assertThat(login.get("username").asText()).isEqualTo(UTILIZADOR);
        assertThat(login.get("password").asText()).isEqualTo(SENHA);
        ServidorIncmTeste.PedidoRecebido put = servidor.pedidos().get(1);
        assertThat(put.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + JWT);
        assertThat(put.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        assertThat(put.consulta()).isNull();
    }

    @Test
    void deveReutilizarOTokenEmVariasOperacoes() {
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);
        ClientePcgt cliente = criarCliente();

        cliente.atualizar(atualizacao("00042"), REFERENCIA);
        cliente.atualizar(atualizacao("00042"), REFERENCIA);
        cliente.criar(criacao("00043"), REFERENCIA);

        assertThat(pedidosA(LOGIN)).hasSize(1);
        assertThat(pedidosA(PROCESSOS)).hasSize(3)
                .allSatisfy(p -> assertThat(p.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + JWT));
    }

    @Test
    void deveRenovarOTokenDepoisDeUm401ERepetirAOperacaoUmaVez() {
        servidor.responderUmaVez(LOGIN, 200, "{\"token\": \"" + JWT + "\"}");
        servidor.responder(LOGIN, 200, "{\"token\": \"" + JWT_NOVO + "\"}");
        servidor.responderUmaVez(PROCESSOS, 401, null);
        servidor.responder(PROCESSOS, 201, PROCESSO);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().criar(criacao("00042"), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(201);
        assertThat(pedidosA(LOGIN)).hasSize(2);
        assertThat(pedidosA(PROCESSOS)).extracting(p -> p.cabecalhos().getFirst("Authorization"))
                .containsExactly("Bearer " + JWT, "Bearer " + JWT_NOVO);
    }

    @Test
    void deveDevolverErroHttp401SemNovaTentativaQuandoARepeticaoTambemDa401() {
        loginAceite();
        servidor.responder(PROCESSOS, 401, null);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(401);
        assertThat(pedidosA(LOGIN)).hasSize(2);
        assertThat(pedidosA(PROCESSOS)).hasSize(2);
    }

    @Test
    void deveDevolver401SemRepetirOLoginEComUmaLinhaErrorSemASenhaQuandoAsCredenciaisSaoRecusadas(LogJson log) {
        servidor.responder(LOGIN, 401, "{\"code\": 401, \"message\": \"Credenciais inválidas.\"}");

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(401);
        assertThat(resultado.erro()).isEqualTo(new ErroPcgt(401, "Credenciais inválidas."));
        assertThat(pedidosA(LOGIN)).hasSize(1);
        assertThat(pedidosA(PROCESSOS)).isEmpty();
        assertThat(log.todas()).contains("ERROR").contains("Credenciais da PCGT recusadas").doesNotContain(SENHA);
    }

    @Test
    void deveDevolverOProcessoNoPut200() {
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.processoNaoExiste()).isFalse();
        assertThat(resultado.erro()).isNull();
        assertThat(resultado.corpo().numeroProcesso()).isEqualTo("00042");
    }

    @Test
    void deveDizerQueOProcessoNaoExisteNoPut404ComOErroESemContarNoCircuito() {
        loginAceite();
        servidor.responder(PROCESSOS, 404, NAO_EXISTE);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00077"), REFERENCIA);

        assertThat(resultado.processoNaoExiste()).isTrue();
        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.erro()).isEqualTo(new ErroPcgt(404, "Processo não encontrado para o codigoProcesso: 00077"));
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void deveDevolverOProcessoNoPost201() {
        loginAceite();
        servidor.responder(PROCESSOS, 201, PROCESSO);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().criar(criacao("00042"), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.corpo().token()).isEqualTo("a3f1c2d4-0000-4000-8000-000000000042");
    }

    @Test
    void deveDevolverErroHttp400ComOErroNoPostSemContarNoCircuito() {
        loginAceite();
        servidor.responder(PROCESSOS, 400, "{\"code\": 400, \"message\": \"codigoProcesso já existe.\"}");

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().criar(criacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(400);
        assertThat(resultado.processoNaoExiste()).isFalse();
        assertThat(resultado.erro()).isEqualTo(new ErroPcgt(400, "codigoProcesso já existe."));
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429})
    void deveDevolverOs4xxSemNovaTentativaESemContarNoCircuito(int codigo) {
        loginAceite();
        servidor.responder(PROCESSOS, codigo, "{\"code\": " + codigo + ", \"message\": \"recusado\"}");

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().criar(criacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(codigo);
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void deveContarO5xxNoCircuitoSemNovaTentativa() {
        loginAceite();
        servidor.responder(PROCESSOS, 503, null);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(503);
        assertThat(resultado.erro()).isNull();
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveDevolverTimeoutEContarNoCircuito() {
        loginAceite();
        servidor.reterResposta(PROCESSOS);

        ResultadoPcgt<ProcessoPcgt> resultado = criarCliente(servidor.url(), Duration.ofMillis(300))
                .atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveSerializarOPostComOsNomesDoContratoESemNulos() throws Exception {
        loginAceite();
        servidor.responder(PROCESSOS, 201, PROCESSO);
        PedidoCriacaoProcessoPcgt pedido = new PedidoCriacaoProcessoPcgt("00042", null, null, "Alteração — PDM", "01",
                "02", "1111", IDUS, new EntidadePcgt("CM-EXEMPLO"), "Aviso", null, LocalDate.of(2026, 10, 24), null,
                new BlocoComissaoConsultivaPcgt("Despacho n.º 1", null,
                        List.of(new FicheiroReferenciaPcgt("despacho.pdf", "https://saigt.exemplo.pt/publico/t/despacho.pdf"))),
                null, new BlocoAprovacaoPcgt(null, null, LocalDate.of(2026, 9, 20), List.of()),
                new BlocoDepositoPcgt("10/2026", LocalDate.of(2026, 10, 24)));

        criarCliente().criar(pedido, REFERENCIA);

        JsonNode corpo = objectMapper.readTree(pedidosA(PROCESSOS).getFirst().corpo());
        assertThat(corpo.get("codigoProcesso").asText()).isEqualTo("00042");
        assertThat(corpo.get("tipoInstrumentoCodigo").asText()).isEqualTo("01");
        assertThat(corpo.get("tipoProcedimentoCodigo").asText()).isEqualTo("02");
        assertThat(corpo.get("entidade").get("codigoEntidade").asText()).isEqualTo("CM-EXEMPLO");
        assertThat(corpo.get("entidade").size()).isEqualTo(1);
        assertThat(corpo.get("municipioToken").asText()).isEqualTo("1111");
        assertThat(corpo.get("codigoIdus").asText()).isEqualTo(IDUS);
        assertThat(corpo.get("dataAto").asText()).isEqualTo("2026-10-24");
        JsonNode ficheiro = corpo.get("constituicaoComissaoConsultiva").get("ficheiros").get(0);
        assertThat(ficheiro.get("nome").asText()).isEqualTo("despacho.pdf");
        assertThat(ficheiro.get("url").asText()).isEqualTo("https://saigt.exemplo.pt/publico/t/despacho.pdf");
        assertThat(corpo.get("aprovacao").get("dataDeliberacaoAm").asText()).isEqualTo("2026-09-20");
        assertThat(corpo.get("deposito").get("dataDeposito").asText()).isEqualTo("2026-10-24");
        assertThat(corpo.has("idSSAIGT")).isFalse();
        assertThat(corpo.has("numeroProcessoAntigo")).isFalse();
        assertThat(corpo.has("numeroAto")).isFalse();
        assertThat(corpo.has("linkAto")).isFalse();
        assertThat(corpo.has("ratificacao")).isFalse();
        assertThat(corpo.get("constituicaoComissaoConsultiva").has("url")).isFalse();
        assertThat(corpo.get("aprovacao").has("dataDeliberacaoCmEim")).isFalse();
    }

    @Test
    void deveSerializarOIdSsaigtComONomeDoContrato() throws Exception {
        String json = objectMapper.findAndRegisterModules().writeValueAsString(new PedidoCriacaoProcessoPcgt("00042",
                "SAIGT-1", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(objectMapper.readTree(json).get("idSSAIGT").asText()).isEqualTo("SAIGT-1");
    }

    @Test
    void deveSerializarOPutSemOsCamposQueAPcgtIgnora() throws Exception {
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);

        criarCliente().atualizar(atualizacao("00042"), REFERENCIA);

        JsonNode corpo = objectMapper.readTree(pedidosA(PROCESSOS).getFirst().corpo());
        assertThat(corpo.get("codigoProcesso").asText()).isEqualTo("00042");
        assertThat(corpo.get("dataAto").asText()).isEqualTo("2026-10-24");
        assertThat(List.of("idSSAIGT", "designacao", "tipoInstrumentoCodigo", "tipoProcedimentoCodigo", "entidade",
                "estado", "tecnicosTokens", "gestorToken", "numeroProcessoAntigo"))
                .allSatisfy(campo -> assertThat(corpo.has(campo)).as(campo).isFalse());
    }

    @Test
    void deveRecusarPedidosSemCodigoProcessoNoConstrutor() {
        assertThatIllegalArgumentException().isThrownBy(() -> criacao(null));
        assertThatIllegalArgumentException().isThrownBy(() -> criacao(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> atualizacao(null));
        assertThatIllegalArgumentException().isThrownBy(() -> atualizacao(""));
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void deveEsperarPelaVezNoLimiteDePedidos() {
        limites = RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMillis(500)).timeoutDuration(Duration.ofSeconds(3)).build());
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);
        ClientePcgt cliente = criarCliente();

        cliente.atualizar(atualizacao("00042"), REFERENCIA);
        ResultadoPcgt<ProcessoPcgt> terceiro = cliente.atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(terceiro.sucesso()).isTrue();
        assertThat(pedidosA(PROCESSOS)).hasSize(2);
    }

    @Test
    void deveDevolverTimeoutSemPedidoEComUmWarnQuandoNaoHaVezNoLimite(LogJson log) {
        limites = RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ofMillis(200)).build());
        loginAceite();
        servidor.responder(PROCESSOS, 200, PROCESSO);
        ClientePcgt cliente = criarCliente();

        cliente.atualizar(atualizacao("00042"), REFERENCIA);
        ResultadoPcgt<ProcessoPcgt> semVez = cliente.atualizar(atualizacao("00042"), REFERENCIA);

        assertThat(semVez.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(semVez.chamada().httpStatus()).isNull();
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(log.todas()).contains("WARN").contains("Sem vez no limite de pedidos da PCGT")
                .contains("ATUALIZAR_PROCESSO").contains(PROCESSO_ID.toString());
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoComOUrlVazio() {
        ClientePcgt cliente = criarCliente("", Duration.ofSeconds(5));

        assertThat(cliente.atualizar(atualizacao("00042"), REFERENCIA).chamada().falhaTecnica())
                .isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(cliente.criar(criacao("00042"), REFERENCIA).chamada().falhaTecnica())
                .isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void naoDeveEscreverOJwtASenhaNemOsCorposNoLog(LogJson log) {
        servidor.responderUmaVez(LOGIN, 200, "{\"token\": \"" + JWT + "\"}");
        servidor.responder(LOGIN, 200, "{\"token\": \"" + JWT_NOVO + "\"}");
        servidor.responderUmaVez(PROCESSOS, 401, null);
        servidor.responderUmaVez(PROCESSOS, 404, NAO_EXISTE);
        servidor.responder(PROCESSOS, 400, "{\"code\": 400, \"message\": \"designacao em falta\"}");
        ClientePcgt cliente = criarCliente();

        cliente.atualizar(atualizacao("00077"), REFERENCIA);
        cliente.criar(criacao("00077"), REFERENCIA);

        assertThat(log.chamadas("ATUALIZAR_PROCESSO")).isNotEmpty();
        assertThat(log.chamadas("CRIAR_PROCESSO")).isNotEmpty();
        assertThat(log.todas())
                .doesNotContain(JWT).doesNotContain(JWT_NOVO).doesNotContain(SENHA).doesNotContain(UTILIZADOR)
                .doesNotContain(IDUS).doesNotContain("Alteração — PDM de Exemplo").doesNotContain("designacao em falta")
                .doesNotContain("Processo não encontrado");
    }
}
