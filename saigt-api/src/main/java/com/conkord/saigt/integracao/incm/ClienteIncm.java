package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.PropagacaoCorrelationIdInterceptor;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.UUID;

/**
 * Único ponto do `saigt-api` com HTTP à INCM; o Circuit Breaker e a linha de integração são do
 * {@link ExecutorChamadasExternas}. Sem retry: o `SubmitNewAct` não é idempotente.
 */
@Component
@EnableConfigurationProperties(IncmProperties.class)
public class ClienteIncm {

    // Circuitos separados: um payload que a INCM rejeita com 5xx só deve parar o envio, não o login de todas as entidades.
    public static final String CIRCUITO_AUTENTICACAO = "incm-autenticacao";
    public static final String CIRCUITO_SUBMISSAO = "incm-submissao";
    private static final String SERVICE_KEY = "ServiceKey";

    private final RestClient restClient;
    private final ExecutorChamadasExternas executor;
    private final String baseUrl;
    private final String serviceKey;

    public ClienteIncm(RestClient.Builder restClientBuilder, ExecutorChamadasExternas executor,
                       IncmProperties propriedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(propriedades.readTimeout());
        this.restClient = restClientBuilder.baseUrl(propriedades.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor())
                .build();
        this.executor = executor;
        this.baseUrl = propriedades.baseUrl();
        this.serviceKey = propriedades.serviceKey();
    }

    ResultadoChamadaIncm<ValidateUserResponse> validarUtilizador(String email, String senha) {
        return executar(Operacao.VALIDATE_USER, null, null, new ValidateUserRequest(email, senha),
                ValidateUserResponse.class);
    }

    public ResultadoChamadaIncm<SubmitNewActResponse> submeterAto(UUID processoId, SubmitNewActRequest pedido) {
        Objects.requireNonNull(pedido, "pedido");
        return executar(Operacao.SUBMIT_NEW_ACT, processoId, null, pedido, SubmitNewActResponse.class);
    }

    public ResultadoChamadaIncm<GetActDetailsResponse> obterDetalhesAto(UUID processoId, long actIdentifier) {
        return executar(Operacao.GET_ACT_DETAILS, processoId, actIdentifier, new GetActDetailsRequest(actIdentifier),
                GetActDetailsResponse.class);
    }

    private <T> ResultadoChamadaIncm<T> executar(Operacao operacao, UUID processoId, Long actIdentifier, Object pedido,
                                                 Class<T> tipoResposta) {
        ResultadoChamadaExterna<T> resultado = executor.executar(SistemaExterno.INCM, operacao.name(), operacao.circuito,
                HttpMethod.POST.name(), baseUrl + operacao.caminho, new ReferenciaChamada(processoId, null, actIdentifier),
                operacao.registaResposta,
                () -> restClient.post()
                        .uri(operacao.caminho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SERVICE_KEY, serviceKey)
                        .body(pedido)
                        .retrieve()
                        .toEntity(tipoResposta));
        if (resultado.temFalhaTecnica()) {
            return ResultadoChamadaIncm.falha(converter(resultado.falhaTecnica()), resultado.httpStatus(),
                    resultado.duracaoMs());
        }
        return ResultadoChamadaIncm.sucesso(resultado.httpStatus(), resultado.corpo(), resultado.duracaoMs());
    }

    /** `FalhaTecnicaIncm` é o valor gravado em `incm_comunicacao.falha_tecnica`. */
    private static FalhaTecnicaIncm converter(FalhaTecnicaExterna falha) {
        return switch (falha) {
            case TIMEOUT -> FalhaTecnicaIncm.TIMEOUT;
            case CIRCUITO_ABERTO -> FalhaTecnicaIncm.CIRCUITO_ABERTO;
            case ERRO_REDE -> FalhaTecnicaIncm.ERRO_REDE;
            case ERRO_HTTP -> FalhaTecnicaIncm.ERRO_HTTP;
            case NAO_CONFIGURADO -> throw new IllegalStateException("O cliente INCM não usa NAO_CONFIGURADO.");
        };
    }

    private enum Operacao {
        // A resposta do ValidateUser traz dados pessoais: fica fora do log.
        VALIDATE_USER("/ValidateUser", CIRCUITO_AUTENTICACAO, false),
        SUBMIT_NEW_ACT("/SubmitNewAct", CIRCUITO_SUBMISSAO, true),
        GET_ACT_DETAILS("/GetActDetails", CIRCUITO_SUBMISSAO, true);

        private final String caminho;
        private final String circuito;
        private final boolean registaResposta;

        Operacao(String caminho, String circuito, boolean registaResposta) {
            this.caminho = caminho;
            this.circuito = circuito;
            this.registaResposta = registaResposta;
        }
    }

    private record ValidateUserRequest(
            @JsonProperty("UserEmailAddress") String userEmailAddress,
            @JsonProperty("UserPassword") String userPassword) {
    }
}
