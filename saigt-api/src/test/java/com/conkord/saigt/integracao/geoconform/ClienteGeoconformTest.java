package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.configuration.LogJson;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@ExtendWith(LogJson.Extensao.class)
class ClienteGeoconformTest {

    private static final String CLIENTE = "cliente-gc-teste";
    private static final String SEGREDO = "segredo-gc-teste-81fa";
    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.token-gc-1.assinatura";
    private static final String TOKEN_NOVO = "eyJhbGciOiJSUzI1NiJ9.token-gc-2.assinatura";
    private static final String CAMINHO_TOKEN = "/oauth/token";
    private static final String PROCESSOS = "/api/geoconform/v1/processos";
    private static final String IDUS = "01.1199.01.012399.02.90001.01.031.01";
    private static final String LOCATION = PROCESSOS + "/" + IDUS;
    private static final String DINAMICA = "Revisão do PDM de Exemplo";
    private static final UUID PROCESSO_ID = UUID.fromString("6a1d0e7b-3c2f-4d5e-8f90-1a2b3c4d5e6f");
    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(PROCESSO_ID, IDUS, null);
    private static final String VISIBILIDADE = PROCESSOS + "/" + IDUS + "/visibilidade";
    private static final LocalDate DATA_CADUCIDADE = LocalDate.of(2028, 8, 4);
    private static final PedidoVisibilidadeGeoconform RETIRAR =
            new PedidoVisibilidadeGeoconform(false, "caducidade", DATA_CADUCIDADE);
    private static final PedidoVisibilidadeGeoconform REPOR = new PedidoVisibilidadeGeoconform(true, null, null);
    private static final String VISIBILIDADE_ALTERADA = """
            {"idus": "%s", "visivel": false, "motivo": "caducidade", "data": "2028-08-04"}
            """.formatted(IDUS);
    private static final Map<String, String> PROBLEMA = Map.of("Content-Type", "application/problem+json");
    private static final String CRIADO = """
            {"idus": "%s", "numprocSaigt": "00107", "estado": "aguarda_ativacao", "tipoPedido": "com_informacao_vetorial",
             "urlAcesso": "https://geoconform.exemplo.pt/acesso/%s"}
            """.formatted(IDUS, IDUS);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServidorIncmTeste servidor;
    private CircuitBreakerRegistry circuitos;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
        // Espelha instances.geoconform.ignore-exceptions das properties.
        circuitos = CircuitBreakerRegistry.ofDefaults();
        circuitos.circuitBreaker(ClienteGeoconform.CIRCUITO,
                CircuitBreakerConfig.custom().ignoreExceptions(HttpClientErrorException.class).build());
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private ClienteGeoconform criarCliente(String baseUrl, String tokenUrl, Duration readTimeout) {
        GeoconformProperties propriedades = new GeoconformProperties(baseUrl, tokenUrl, CLIENTE, SEGREDO,
                Duration.ofSeconds(2), readTimeout);
        return new ClienteGeoconform(RestClient.builder(), new ExecutorChamadasExternas(circuitos, evento -> { }), propriedades,
                Clock.systemUTC());
    }

    private ClienteGeoconform criarCliente() {
        return criarCliente(servidor.url(), servidor.url() + CAMINHO_TOKEN, Duration.ofSeconds(5));
    }

    private CircuitBreaker circuito() {
        return circuitos.circuitBreaker(ClienteGeoconform.CIRCUITO);
    }

    private void tokenAceite() {
        servidor.responder(CAMINHO_TOKEN, 200,
                "{\"access_token\": \"" + TOKEN + "\", \"token_type\": \"Bearer\", \"expires_in\": 3600}");
    }

    private static String problema(int status, String codigo) {
        return """
                {"type": "https://geoconform.dgterritorio.pt/erros/%s", "title": "Erro", "status": %d, "codigo": "%s",
                 "detail": "detalhe com dados do pedido %s", "instance": "/api/geoconform/v1/processos"}
                """.formatted(codigo, status, codigo, DINAMICA);
    }

    private static PedidoCriacaoProcessoGeoconform pedido() {
        return new PedidoCriacaoProcessoGeoconform(IDUS, "00107", "Plano Diretor Municipal de Exemplo", DINAMICA,
                new EntidadeGeoconform("Câmara Municipal de Exemplo", "500000000"), true);
    }

    private List<ServidorIncmTeste.PedidoRecebido> pedidosA(String caminho) {
        return servidor.pedidos().stream().filter(p -> p.caminho().equals(caminho)).toList();
    }

    @Test
    void deveCriarComOBearerDoTokenELerOCorpoEOLocationDo201() throws Exception {
        tokenAceite();
        servidor.responder(PROCESSOS, 201, CRIADO, Map.of("Location", LOCATION));

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.duplicado()).isFalse();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo()).isEqualTo(new ProcessoGeoconform(IDUS, "00107", "aguarda_ativacao",
                "https://geoconform.exemplo.pt/acesso/" + IDUS));
        assertThat(resultado.location()).isEqualTo(LOCATION);
        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho)
                .containsExactly(CAMINHO_TOKEN, PROCESSOS);
        ServidorIncmTeste.PedidoRecebido post = pedidosA(PROCESSOS).getFirst();
        assertThat(post.metodo()).isEqualTo("POST");
        assertThat(post.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(post.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        assertThat(post.cabecalhos().containsKey("Idempotency-Key")).isFalse();
        assertThat(circuito().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
    }

    @Test
    void deveSerializarComOsNomesDoContratoSemComponentesESemNulos() throws Exception {
        tokenAceite();
        servidor.responder(PROCESSOS, 201, CRIADO);

        criarCliente().criarProcesso(pedido(), REFERENCIA);
        criarCliente().criarProcesso(new PedidoCriacaoProcessoGeoconform(IDUS, "00107", "PDM", "Alteração",
                new EntidadeGeoconform("Câmara Municipal de Exemplo", null), null), REFERENCIA);

        JsonNode completo = objectMapper.readTree(pedidosA(PROCESSOS).get(0).corpo());
        assertThat(completo.fieldNames()).toIterable().containsExactly("idus", "numprocSaigt", "descricaoIgt",
                "descricaoDinamica", "entidade", "criarFichaDadosEstatisticos");
        assertThat(completo.get("criarFichaDadosEstatisticos").asBoolean()).isTrue();
        assertThat(completo.get("entidade").get("designacao").asText()).isEqualTo("Câmara Municipal de Exemplo");
        assertThat(completo.get("entidade").get("nifEntidade").asText()).isEqualTo("500000000");
        assertThat(completo.has("componentes")).isFalse();
        JsonNode semNulos = objectMapper.readTree(pedidosA(PROCESSOS).get(1).corpo());
        assertThat(semNulos.has("criarFichaDadosEstatisticos")).isFalse();
        assertThat(semNulos.get("entidade").has("nifEntidade")).isFalse();
        assertThat(pedidosA(PROCESSOS).get(1).corpo()).doesNotContain("null");
    }

    @Test
    void deveReutilizarOTokenEmVariasCriacoes() {
        tokenAceite();
        servidor.responder(PROCESSOS, 201, CRIADO);
        ClienteGeoconform cliente = criarCliente();

        cliente.criarProcesso(pedido(), REFERENCIA);
        cliente.criarProcesso(pedido(), REFERENCIA);
        cliente.criarProcesso(pedido(), REFERENCIA);

        assertThat(pedidosA(CAMINHO_TOKEN)).hasSize(1);
        assertThat(pedidosA(PROCESSOS)).hasSize(3)
                .allSatisfy(p -> assertThat(p.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN));
    }

    @Test
    void deveTratarO409IduDuplicadoComoCriadoComUmWarnESemContarNoCircuito(LogJson log) {
        tokenAceite();
        servidor.responder(PROCESSOS, 409, problema(409, "idu_duplicado"), PROBLEMA);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.duplicado()).isTrue();
        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.codigoErro()).isEqualTo("idu_duplicado");
        assertThat(resultado.chamada().httpStatus()).isEqualTo(409);
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(log.linhas().stream().filter(l -> l.contains("WARN") && l.contains("idu_duplicado")
                && l.contains(PROCESSO_ID.toString()))).hasSize(1);
        // Só o pedido do token conta (com sucesso); o 409 é ignorado pelo circuito.
        assertThat(circuito().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void outro409NaoContaComoCriado() {
        tokenAceite();
        servidor.responder(PROCESSOS, 409, problema(409, "estado_incompativel"), PROBLEMA);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.duplicado()).isFalse();
        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
    }

    @ParameterizedTest
    @CsvSource({"422, payload_invalido", "403, nao_autorizado"})
    void deveDevolverErroHttpComOCodigoSemNovaTentativaESemContarNoCircuito(int status, String codigo) {
        tokenAceite();
        servidor.responder(PROCESSOS, status, problema(status, codigo), PROBLEMA);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(status);
        assertThat(resultado.codigoErro()).isEqualTo(codigo);
        assertThat(resultado.criado()).isFalse();
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuito().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
    }

    @Test
    void deveContarO5xxNoCircuitoSemNovaTentativa() {
        tokenAceite();
        servidor.responder(PROCESSOS, 503, null);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(503);
        assertThat(resultado.criado()).isFalse();
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveDevolverTimeoutEContarNoCircuitoSemNovaTentativa() {
        tokenAceite();
        servidor.reterResposta(PROCESSOS);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente(servidor.url(), servidor.url() + CAMINHO_TOKEN,
                Duration.ofMillis(300)).criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(pedidosA(PROCESSOS)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveDevolverErroDeRedeComOGeoconformInacessivel() {
        String inacessivel = ServidorIncmTeste.urlInacessivel();

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente(inacessivel, inacessivel + CAMINHO_TOKEN,
                Duration.ofSeconds(1)).criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_REDE);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveRenovarOTokenDepoisDeUm401ERepetirAOperacaoUmaVez() {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN + "\", \"expires_in\": 3600}");
        servidor.responder(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN_NOVO + "\", \"expires_in\": 3600}");
        servidor.responderUmaVez(PROCESSOS, 401, null);
        servidor.responder(PROCESSOS, 201, CRIADO);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(pedidosA(CAMINHO_TOKEN)).hasSize(2);
        assertThat(pedidosA(PROCESSOS)).extracting(p -> p.cabecalhos().getFirst("Authorization"))
                .containsExactly("Bearer " + TOKEN, "Bearer " + TOKEN_NOVO);
    }

    @Test
    void deveDevolverErroHttp401SemMaisTentativasQuandoARepeticaoTambemDa401() {
        tokenAceite();
        servidor.responder(PROCESSOS, 401, null);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(401);
        assertThat(pedidosA(CAMINHO_TOKEN)).hasSize(2);
        assertThat(pedidosA(PROCESSOS)).hasSize(2);
    }

    @Test
    void naoDeveCriarQuandoOPedidoDoTokenFalha(LogJson log) {
        servidor.responder(CAMINHO_TOKEN, 503, null);

        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(pedido(), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(503);
        assertThat(pedidosA(PROCESSOS)).isEmpty();
        assertThat(log.todas()).contains("Pedido do token do GeoConform falhou").doesNotContain(SEGREDO);
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoComOUrlBaseOuDoTokenVazio() {
        tokenAceite();
        servidor.responder(PROCESSOS, 201, CRIADO);

        assertThat(criarCliente("", servidor.url() + CAMINHO_TOKEN, Duration.ofSeconds(5)).criarProcesso(pedido(),
                REFERENCIA).chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(criarCliente(servidor.url(), " ", Duration.ofSeconds(5)).criarProcesso(pedido(), REFERENCIA)
                .chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void deveRecusarPedidoNulo() {
        assertThatNullPointerException().isThrownBy(() -> criarCliente().criarProcesso(null, REFERENCIA));
    }

    @Test
    void naoDeveEscreverOTokenOSegredoNemOsCorposNoLog(LogJson log) {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN + "\", \"expires_in\": 3600}");
        servidor.responder(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN_NOVO + "\", \"expires_in\": 3600}");
        servidor.responderUmaVez(PROCESSOS, 401, null);
        servidor.responderUmaVez(PROCESSOS, 422, problema(422, "payload_invalido"), PROBLEMA);
        servidor.responder(PROCESSOS, 201, CRIADO, Map.of("Location", LOCATION));
        ClienteGeoconform cliente = criarCliente();

        cliente.criarProcesso(pedido(), REFERENCIA);
        cliente.criarProcesso(pedido(), REFERENCIA);

        assertThat(log.todas()).contains("GEOCONFORM").contains("CRIAR_PROCESSO")
                .doesNotContain(TOKEN).doesNotContain(TOKEN_NOVO).doesNotContain(SEGREDO).doesNotContain(CLIENTE)
                .doesNotContain(DINAMICA).doesNotContain("Câmara Municipal de Exemplo").doesNotContain("500000000")
                .doesNotContain("aguarda_ativacao").doesNotContain("geoconform.exemplo.pt/acesso");
        assertThat(log.chamadas("TOKEN")).isNotEmpty();
    }

    @Test
    void deveRetirarComPatchNoCaminhoDoIdusComOBearerEOCorpoDoContrato() throws Exception {
        tokenAceite();
        servidor.responder(VISIBILIDADE, 200, VISIBILIDADE_ALTERADA);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(servidor.pedidos()).extracting(ServidorIncmTeste.PedidoRecebido::caminho)
                .containsExactly(CAMINHO_TOKEN, VISIBILIDADE);
        ServidorIncmTeste.PedidoRecebido patch = pedidosA(VISIBILIDADE).getFirst();
        assertThat(patch.metodo()).isEqualTo("PATCH");
        assertThat(patch.consulta()).isNull();
        assertThat(patch.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(patch.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        JsonNode corpo = objectMapper.readTree(patch.corpo());
        assertThat(corpo.fieldNames()).toIterable().containsExactly("visivel", "motivo", "data");
        assertThat(corpo.get("visivel").isBoolean()).isTrue();
        assertThat(corpo.get("visivel").asBoolean()).isFalse();
        assertThat(corpo.get("motivo").asText()).isEqualTo("caducidade");
        assertThat(corpo.get("data").asText()).isEqualTo("2028-08-04");
        assertThat(circuito().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
    }

    @Test
    void deveReporSoComVisivelTrue() throws Exception {
        tokenAceite();
        servidor.responder(VISIBILIDADE, 200, VISIBILIDADE_ALTERADA);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, REPOR, REFERENCIA);

        assertThat(resultado.httpStatus()).isEqualTo(200);
        JsonNode corpo = objectMapper.readTree(pedidosA(VISIBILIDADE).getFirst().corpo());
        assertThat(corpo.fieldNames()).toIterable().containsExactly("visivel");
        assertThat(corpo.get("visivel").asBoolean()).isTrue();
    }

    @Test
    void deveTratarComoSucessoO200RepetidoSobreUmProcessoJaNoEstadoPedido() {
        tokenAceite();
        servidor.responder(VISIBILIDADE, 200, VISIBILIDADE_ALTERADA);
        ClienteGeoconform cliente = criarCliente();

        ResultadoChamadaExterna<Void> primeira = cliente.alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);
        ResultadoChamadaExterna<Void> repetida = cliente.alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(primeira.temFalhaTecnica()).isFalse();
        assertThat(repetida.temFalhaTecnica()).isFalse();
        assertThat(repetida.httpStatus()).isEqualTo(200);
        assertThat(pedidosA(VISIBILIDADE)).hasSize(2);
        assertThat(pedidosA(CAMINHO_TOKEN)).hasSize(1);
    }

    @Test
    void deveCodificarOIdusComoVariavelDoCaminhoSemOConcatenar() {
        tokenAceite();
        String idusMalicioso = "01.1199?visivel=true#x";

        criarCliente().alterarVisibilidade(idusMalicioso, RETIRAR, REFERENCIA);

        ServidorIncmTeste.PedidoRecebido patch = servidor.pedidos().stream()
                .filter(p -> p.metodo().equals("PATCH")).findFirst().orElseThrow();
        assertThat(patch.caminho()).isEqualTo(PROCESSOS + "/" + idusMalicioso + "/visibilidade");
        assertThat(patch.consulta()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"404, idu_desconhecido", "409, estado_incompativel", "422, payload_invalido", "403, nao_autorizado"})
    void deveDevolverErroHttpNaVisibilidadeSemNovaTentativaESemContarNoCircuito(int status, String codigo) {
        tokenAceite();
        servidor.responder(VISIBILIDADE, status, problema(status, codigo), PROBLEMA);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(status);
        assertThat(pedidosA(VISIBILIDADE)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuito().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
    }

    @Test
    void deveContarO5xxDaVisibilidadeNoCircuitoSemNovaTentativa() {
        tokenAceite();
        servidor.responder(VISIBILIDADE, 503, null);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(503);
        assertThat(pedidosA(VISIBILIDADE)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveDevolverTimeoutNaVisibilidadeEContarNoCircuitoSemNovaTentativa() {
        tokenAceite();
        servidor.reterResposta(VISIBILIDADE);

        ResultadoChamadaExterna<Void> resultado = criarCliente(servidor.url(), servidor.url() + CAMINHO_TOKEN,
                Duration.ofMillis(300)).alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.TIMEOUT);
        assertThat(pedidosA(VISIBILIDADE)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void deveRenovarOTokenDepoisDeUm401NaVisibilidadeERepetirUmaVez() {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN + "\", \"expires_in\": 3600}");
        servidor.responder(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN_NOVO + "\", \"expires_in\": 3600}");
        servidor.responderUmaVez(VISIBILIDADE, 401, null);
        servidor.responder(VISIBILIDADE, 200, VISIBILIDADE_ALTERADA);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(pedidosA(VISIBILIDADE)).extracting(p -> p.cabecalhos().getFirst("Authorization"))
                .containsExactly("Bearer " + TOKEN, "Bearer " + TOKEN_NOVO);
    }

    @Test
    void naoDeveAlterarAVisibilidadeQuandoOPedidoDoTokenFalha() {
        servidor.responder(CAMINHO_TOKEN, 503, null);

        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(503);
        assertThat(pedidosA(VISIBILIDADE)).isEmpty();
    }

    @Test
    void deveDevolverNaoConfiguradoNaVisibilidadeSemPedido() {
        ResultadoChamadaExterna<Void> resultado = criarCliente("", servidor.url() + CAMINHO_TOKEN, Duration.ofSeconds(5))
                .alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void deveRecusarIdusOuPedidoEmFaltaNaVisibilidade() {
        ClienteGeoconform cliente = criarCliente();
        assertThatNullPointerException().isThrownBy(() -> cliente.alterarVisibilidade(null, RETIRAR, REFERENCIA));
        assertThatNullPointerException().isThrownBy(() -> cliente.alterarVisibilidade(IDUS, null, REFERENCIA));
        assertThatIllegalArgumentException().isThrownBy(() -> cliente.alterarVisibilidade(" ", RETIRAR, REFERENCIA));
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void naoDeveEscreverOTokenNemOsCorposDaVisibilidadeNoLog(LogJson log) {
        tokenAceite();
        servidor.responderUmaVez(VISIBILIDADE, 404, problema(404, "idu_desconhecido"), PROBLEMA);
        servidor.responder(VISIBILIDADE, 200, VISIBILIDADE_ALTERADA);
        ClienteGeoconform cliente = criarCliente();

        cliente.alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);
        cliente.alterarVisibilidade(IDUS, RETIRAR, REFERENCIA);

        assertThat(log.todas()).contains("ALTERAR_VISIBILIDADE").contains("PATCH")
                .doesNotContain(TOKEN).doesNotContain(SEGREDO).doesNotContain("\"visivel\"")
                .doesNotContain("caducidade").doesNotContain("2028-08-04").doesNotContain(DINAMICA);
    }

    private static final String PUBLICACAO = PROCESSOS + "/" + IDUS + "/publicacao";
    private static final String IDUS_DINAMICA = "01.1199.01.012399.02.00003.20261110";
    private static final String IDENTIFICADORES = "/api/geoconform/v1/tentativas/" + IDUS_DINAMICA + "/identificadores";
    private static final PedidoPublicacaoGeoconform PUBLICAR = new PedidoPublicacaoGeoconform("00107", "18452/2026",
            "https://dre.exemplo.pt/18452-2026", LocalDate.of(2026, 10, 14));
    private static final PedidoCorrecaoIdentificadoresGeoconform CORRIGIR =
            PedidoCorrecaoIdentificadoresGeoconform.publicacaoFinal(IDUS_DINAMICA, List.of(
                    new CorrespondenciaIdusGeoconform(IDUS, "01.1199.01.012399.02.00003.01.031.01", "00107"),
                    new CorrespondenciaIdusGeoconform("01.1199.01.012399.02.90001.01.034.01",
                            "01.1199.01.012399.02.00003.01.034.01", "00109")));

    @Test
    void deveRegistarAPublicacaoComPostNoCaminhoDoIdusEOsQuatroCamposDoContrato() throws Exception {
        tokenAceite();
        servidor.responder(PUBLICACAO, 200, "{\"idus\": \"" + IDUS + "\", \"estado\": \"publicado\"}");

        ResultadoChamadaExterna<Void> resultado = criarCliente().registarPublicacao(IDUS, PUBLICAR, REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        ServidorIncmTeste.PedidoRecebido post = pedidosA(PUBLICACAO).getFirst();
        assertThat(post.metodo()).isEqualTo("POST");
        assertThat(post.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(post.cabecalhos().getFirst("Content-Type")).startsWith("application/json");
        JsonNode corpo = objectMapper.readTree(post.corpo());
        assertThat(corpo.fieldNames()).toIterable()
                .containsExactly("numprocSaigt", "numeroAtoIncm", "linkPublicacaoIncm", "dataPublicacao");
        assertThat(corpo.get("numprocSaigt").asText()).isEqualTo("00107");
        assertThat(corpo.get("numeroAtoIncm").asText()).isEqualTo("18452/2026");
        assertThat(corpo.get("linkPublicacaoIncm").asText()).isEqualTo("https://dre.exemplo.pt/18452-2026");
        assertThat(corpo.get("dataPublicacao").asText()).isEqualTo("2026-10-14");
    }

    @Test
    void deveCorrigirOsIdentificadoresComPutNoCaminhoDoIdusDaDinamica() throws Exception {
        tokenAceite();
        servidor.responder(IDENTIFICADORES, 200,
                "{\"idusDinamica\": \"" + IDUS_DINAMICA + "\", \"correspondenciasAceites\": 2}");

        ResultadoChamadaExterna<Void> resultado = criarCliente().corrigirIdentificadores(CORRIGIR, REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        ServidorIncmTeste.PedidoRecebido put = pedidosA(IDENTIFICADORES).getFirst();
        assertThat(put.metodo()).isEqualTo("PUT");
        assertThat(put.cabecalhos().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        JsonNode corpo = objectMapper.readTree(put.corpo());
        assertThat(corpo.fieldNames()).toIterable().containsExactly("idusDinamica", "motivo", "correspondencias");
        assertThat(corpo.get("idusDinamica").asText()).isEqualTo(IDUS_DINAMICA);
        assertThat(corpo.get("motivo").asText()).isEqualTo("publicacao_final");
        assertThat(corpo.get("correspondencias")).hasSize(2);
        JsonNode primeira = corpo.get("correspondencias").get(0);
        assertThat(primeira.fieldNames()).toIterable().containsExactly("idusAnterior", "idusNovo", "numprocSaigt");
        assertThat(primeira.get("idusAnterior").asText()).isEqualTo(IDUS);
        assertThat(primeira.get("idusNovo").asText()).isEqualTo("01.1199.01.012399.02.00003.01.031.01");
        assertThat(primeira.get("numprocSaigt").asText()).isEqualTo("00107");
    }

    @ParameterizedTest
    @CsvSource({"404, idu_desconhecido", "422, payload_invalido"})
    void deveDevolverErroHttpNaPublicacaoENaCorrecaoSemNovaTentativaESemContarNoCircuito(int status, String codigo) {
        tokenAceite();
        servidor.responder(PUBLICACAO, status, problema(status, codigo), PROBLEMA);
        servidor.responder(IDENTIFICADORES, status, problema(status, codigo), PROBLEMA);
        ClienteGeoconform cliente = criarCliente();

        ResultadoChamadaExterna<Void> publicacao = cliente.registarPublicacao(IDUS, PUBLICAR, REFERENCIA);
        ResultadoChamadaExterna<Void> correcao = cliente.corrigirIdentificadores(CORRIGIR, REFERENCIA);

        assertThat(publicacao.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(publicacao.httpStatus()).isEqualTo(status);
        assertThat(correcao.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(pedidosA(PUBLICACAO)).hasSize(1);
        assertThat(pedidosA(IDENTIFICADORES)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void deveContarO5xxDaPublicacaoENaCorrecaoNoCircuitoSemNovaTentativa() {
        tokenAceite();
        servidor.responder(PUBLICACAO, 503, null);
        servidor.responder(IDENTIFICADORES, 502, null);
        ClienteGeoconform cliente = criarCliente();

        ResultadoChamadaExterna<Void> publicacao = cliente.registarPublicacao(IDUS, PUBLICAR, REFERENCIA);
        ResultadoChamadaExterna<Void> correcao = cliente.corrigirIdentificadores(CORRIGIR, REFERENCIA);

        assertThat(publicacao.httpStatus()).isEqualTo(503);
        assertThat(correcao.httpStatus()).isEqualTo(502);
        assertThat(pedidosA(PUBLICACAO)).hasSize(1);
        assertThat(pedidosA(IDENTIFICADORES)).hasSize(1);
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    void deveRenovarOTokenDepoisDeUm401NaPublicacaoERepetirUmaVez() {
        servidor.responderUmaVez(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN + "\", \"expires_in\": 3600}");
        servidor.responder(CAMINHO_TOKEN, 200, "{\"access_token\": \"" + TOKEN_NOVO + "\", \"expires_in\": 3600}");
        servidor.responderUmaVez(PUBLICACAO, 401, null);
        servidor.responder(PUBLICACAO, 200, "{}");

        ResultadoChamadaExterna<Void> resultado = criarCliente().registarPublicacao(IDUS, PUBLICAR, REFERENCIA);

        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(pedidosA(PUBLICACAO)).extracting(p -> p.cabecalhos().getFirst("Authorization"))
                .containsExactly("Bearer " + TOKEN, "Bearer " + TOKEN_NOVO);
    }

    @Test
    void deveDevolverNaoConfiguradoSemPedidoNaPublicacaoENaCorrecao() {
        ClienteGeoconform cliente = criarCliente("", servidor.url() + CAMINHO_TOKEN, Duration.ofSeconds(5));

        assertThat(cliente.registarPublicacao(IDUS, PUBLICAR, REFERENCIA).falhaTecnica())
                .isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(cliente.corrigirIdentificadores(CORRIGIR, REFERENCIA).falhaTecnica())
                .isEqualTo(FalhaTecnicaExterna.NAO_CONFIGURADO);
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void deveRecusarPedidosIncompletosNaPublicacaoENaCorrecao() {
        ClienteGeoconform cliente = criarCliente();
        assertThatNullPointerException().isThrownBy(() -> cliente.registarPublicacao(null, PUBLICAR, REFERENCIA));
        assertThatNullPointerException().isThrownBy(() -> cliente.registarPublicacao(IDUS, null, REFERENCIA));
        assertThatIllegalArgumentException().isThrownBy(() -> cliente.registarPublicacao(" ", PUBLICAR, REFERENCIA));
        assertThatNullPointerException().isThrownBy(() -> cliente.corrigirIdentificadores(null, REFERENCIA));
        assertThatIllegalArgumentException().isThrownBy(() -> cliente.corrigirIdentificadores(
                PedidoCorrecaoIdentificadoresGeoconform.publicacaoFinal(IDUS_DINAMICA, List.of()), REFERENCIA));
        assertThatIllegalArgumentException().isThrownBy(() -> cliente.corrigirIdentificadores(
                PedidoCorrecaoIdentificadoresGeoconform.publicacaoFinal(" ", CORRIGIR.correspondencias()), REFERENCIA));
        assertThat(servidor.pedidos()).isEmpty();
    }

    @Test
    void naoDeveEscreverOTokenNemOsCorposDaPublicacaoEDaCorrecaoNoLog(LogJson log) {
        tokenAceite();
        servidor.responder(PUBLICACAO, 422, problema(422, "payload_invalido"), PROBLEMA);
        servidor.responder(IDENTIFICADORES, 200, "{\"correspondenciasAceites\": 2}");
        ClienteGeoconform cliente = criarCliente();

        cliente.registarPublicacao(IDUS, PUBLICAR, REFERENCIA);
        cliente.corrigirIdentificadores(CORRIGIR, REFERENCIA);

        assertThat(log.todas()).contains("REGISTAR_PUBLICACAO").contains("CORRIGIR_IDENTIFICADORES")
                .doesNotContain(TOKEN).doesNotContain(SEGREDO).doesNotContain("18452/2026")
                .doesNotContain("dre.exemplo.pt").doesNotContain("publicacao_final").doesNotContain("idusAnterior")
                .doesNotContain(DINAMICA);
    }
}
