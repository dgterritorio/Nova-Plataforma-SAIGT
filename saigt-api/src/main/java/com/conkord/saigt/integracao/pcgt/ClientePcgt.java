package com.conkord.saigt.integracao.pcgt;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.PropagacaoCorrelationIdInterceptor;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Único ponto do `saigt-api` com HTTP à API de processos da PCGT (`contracts/pcgt-processos.pdf`). Trata do login JWT,
 * da renovação do token num 401 e do limite de pedidos; o que vai em cada campo é decidido por quem chama.
 * Sem novas tentativas além da renovação do token: os 4xx voltam como `ERRO_HTTP` e não contam para o circuito
 * (`ignore-exceptions` em `application.properties`).
 */
@Component
@EnableConfigurationProperties(PcgtProperties.class)
public class ClientePcgt {

    public static final String CIRCUITO = "pcgt";
    public static final String LIMITE_PEDIDOS = "pcgt";
    private static final Logger log = LoggerFactory.getLogger(ClientePcgt.class);
    private static final String CAMINHO_LOGIN = "/api/auth/login";
    private static final String CAMINHO_PROCESSOS = "/api/processos";
    private static final String OPERACAO_LOGIN = "LOGIN";
    private static final String OPERACAO_ATUALIZAR = "ATUALIZAR_PROCESSO";
    private static final String OPERACAO_CRIAR = "CRIAR_PROCESSO";
    private static final int NAO_AUTORIZADO = 401;

    private final RestClient restClient;
    private final ExecutorChamadasExternas executor;
    private final RateLimiter limitePedidos;
    private final PcgtProperties propriedades;
    private final boolean configurado;
    private final String baseUrl;
    private final TokenPcgt token = new TokenPcgt();

    public ClientePcgt(RestClient.Builder restClientBuilder, ExecutorChamadasExternas executor,
                       RateLimiterRegistry rateLimiterRegistry, PcgtProperties propriedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(propriedades.readTimeout());
        this.configurado = propriedades.baseUrl() != null && !propriedades.baseUrl().isBlank();
        this.baseUrl = configurado ? propriedades.baseUrl() : "";
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor())
                .build();
        this.executor = executor;
        this.limitePedidos = rateLimiterRegistry.rateLimiter(LIMITE_PEDIDOS);
        this.propriedades = propriedades;
    }

    /** `PUT /api/processos`, pelo `codigoProcesso` do corpo. Um 404 quer dizer que o processo não existe na PCGT. */
    public ResultadoPcgt<ProcessoPcgt> atualizar(PedidoAtualizacaoProcessoPcgt pedido, ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        return enviar(OPERACAO_ATUALIZAR, HttpMethod.PUT, pedido, referencia);
    }

    /** `POST /api/processos`. Repetir depois de um 401 não duplica: o 401 quer dizer que o pedido não foi tratado. */
    public ResultadoPcgt<ProcessoPcgt> criar(PedidoCriacaoProcessoPcgt pedido, ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        return enviar(OPERACAO_CRIAR, HttpMethod.POST, pedido, referencia);
    }

    private ResultadoPcgt<ProcessoPcgt> enviar(String operacao, HttpMethod metodo, Object corpo,
                                               ReferenciaChamada referencia) {
        if (!configurado) {
            return new ResultadoPcgt<>(executor.naoConfigurado(SistemaExterno.PCGT, operacao, metodo.name(),
                    CAMINHO_PROCESSOS, referencia), null);
        }
        TokenPcgt.Autenticacao<ResultadoPcgt<?>> autenticacao = token.obter(null, () -> autenticar(referencia));
        if (autenticacao.token() == null) {
            return comoFalha(autenticacao.falha());
        }
        ResultadoPcgt<ProcessoPcgt> resultado = chamar(operacao, metodo, corpo, autenticacao.token(), referencia);
        if (!naoAutorizado(resultado.chamada())) {
            return resultado;
        }
        String recusado = autenticacao.token();
        autenticacao = token.obter(recusado, () -> autenticar(referencia));
        if (autenticacao.token() == null) {
            return comoFalha(autenticacao.falha());
        }
        return chamar(operacao, metodo, corpo, autenticacao.token(), referencia);
    }

    private TokenPcgt.Autenticacao<ResultadoPcgt<?>> autenticar(ReferenciaChamada referencia) {
        ResultadoPcgt<RespostaLogin> login = pedir(OPERACAO_LOGIN, HttpMethod.POST, CAMINHO_LOGIN, null,
                new PedidoLogin(propriedades.username(), propriedades.password()), RespostaLogin.class, referencia);
        if (naoAutorizado(login.chamada())) {
            log.error("Credenciais da PCGT recusadas (processoId={})", referencia == null ? null : referencia.processoId());
            return new TokenPcgt.Autenticacao<>(null, login);
        }
        if (!login.sucesso()) {
            return new TokenPcgt.Autenticacao<>(null, login);
        }
        String jwt = login.corpo() == null ? null : login.corpo().token();
        if (jwt == null || jwt.isBlank()) {
            ResultadoChamadaExterna<RespostaLogin> semToken = new ResultadoChamadaExterna<>(
                    login.chamada().httpStatus(), null, FalhaTecnicaExterna.ERRO_HTTP, login.chamada().duracaoMs());
            return new TokenPcgt.Autenticacao<>(null, new ResultadoPcgt<>(semToken, null));
        }
        return new TokenPcgt.Autenticacao<>(jwt, null);
    }

    private ResultadoPcgt<ProcessoPcgt> chamar(String operacao, HttpMethod metodo, Object corpo, String jwt,
                                               ReferenciaChamada referencia) {
        return pedir(operacao, metodo, CAMINHO_PROCESSOS, jwt, corpo, ProcessoPcgt.class, referencia);
    }

    /**
     * A vez no limite de pedidos pede-se fora do circuito: um `RequestNotPermitted` dentro dele não seria classificado
     * e contaria como falha. O corpo de erro lê-se aqui porque o executor, partilhado, não o guarda.
     */
    private <T> ResultadoPcgt<T> pedir(String operacao, HttpMethod metodo, String caminho, String jwt, Object corpo,
                                       Class<T> tipo, ReferenciaChamada referencia) {
        Instant inicio = Instant.now();
        if (!limitePedidos.acquirePermission()) {
            log.warn("Sem vez no limite de pedidos da PCGT (operacao={}, processoId={})", operacao,
                    referencia == null ? null : referencia.processoId());
            return new ResultadoPcgt<>(new ResultadoChamadaExterna<>(null, null, FalhaTecnicaExterna.TIMEOUT,
                    Duration.between(inicio, Instant.now()).toMillis()), null);
        }
        AtomicReference<ErroPcgt> erro = new AtomicReference<>();
        ResultadoChamadaExterna<T> chamada = executor.executar(SistemaExterno.PCGT, operacao, CIRCUITO, metodo.name(),
                baseUrl + caminho, referencia, () -> {
                    try {
                        RestClient.RequestBodySpec pedido = restClient.method(metodo).uri(caminho)
                                .contentType(MediaType.APPLICATION_JSON);
                        if (jwt != null) {
                            pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
                        }
                        return pedido.body(corpo).retrieve().toEntity(tipo);
                    } catch (RestClientResponseException e) {
                        erro.set(lerErro(e));
                        throw e;
                    }
                });
        return new ResultadoPcgt<>(chamada, erro.get());
    }

    private static ErroPcgt lerErro(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErroPcgt.class);
        } catch (RuntimeException ilegivel) {
            return null;
        }
    }

    private static boolean naoAutorizado(ResultadoChamadaExterna<?> chamada) {
        return chamada.falhaTecnica() == FalhaTecnicaExterna.ERRO_HTTP
                && Integer.valueOf(NAO_AUTORIZADO).equals(chamada.httpStatus());
    }

    private static ResultadoPcgt<ProcessoPcgt> comoFalha(ResultadoPcgt<?> falha) {
        ResultadoChamadaExterna<?> c = falha.chamada();
        return new ResultadoPcgt<>(new ResultadoChamadaExterna<>(c.httpStatus(), null, c.falhaTecnica(), c.duracaoMs()),
                falha.erro());
    }

    record PedidoLogin(String username, String password) {

        @Override
        public String toString() {
            return "PedidoLogin[username=***, password=***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaLogin(String token) {

        @Override
        public String toString() {
            return "RespostaLogin[token=***]";
        }
    }
}
