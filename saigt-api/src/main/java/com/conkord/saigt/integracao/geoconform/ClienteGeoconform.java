package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.PropagacaoCorrelationIdInterceptor;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.core.SistemaExterno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Único ponto do `saigt-api` com HTTP à API exposta pelo GeoConform (`contracts/geoconform.docx` v0.6). Trata do token
 * OAuth e da repetição única depois de um 401; o que vai em cada campo é decidido por quem chama. Sem outras novas
 * tentativas nem `Idempotency-Key`: os 4xx voltam como `ERRO_HTTP` e não contam para o circuito (`ignore-exceptions`
 * em `application.properties`); 5xx, timeout e rede contam.
 */
@Component
@EnableConfigurationProperties(GeoconformProperties.class)
public class ClienteGeoconform {

    public static final String CIRCUITO = "geoconform";
    static final String CAMINHO_PROCESSOS = "/api/geoconform/v1/processos";
    static final String CAMINHO_VISIBILIDADE = CAMINHO_PROCESSOS + "/{idus}/visibilidade";
    static final String CAMINHO_PUBLICACAO = CAMINHO_PROCESSOS + "/{idus}/publicacao";
    static final String CAMINHO_IDENTIFICADORES = "/api/geoconform/v1/tentativas/{idusDinamica}/identificadores";
    static final String OPERACAO_CRIAR_PROCESSO = "CRIAR_PROCESSO";
    static final String OPERACAO_ALTERAR_VISIBILIDADE = "ALTERAR_VISIBILIDADE";
    static final String OPERACAO_REGISTAR_PUBLICACAO = "REGISTAR_PUBLICACAO";
    static final String OPERACAO_CORRIGIR_IDENTIFICADORES = "CORRIGIR_IDENTIFICADORES";
    private static final Logger log = LoggerFactory.getLogger(ClienteGeoconform.class);
    private static final int NAO_AUTORIZADO = 401;

    private final RestClient restClient;
    private final ExecutorChamadasExternas executor;
    private final TokenGeoconform token;
    private final boolean configurado;
    private final String baseUrl;

    public ClienteGeoconform(RestClient.Builder restClientBuilder, ExecutorChamadasExternas executor,
                             GeoconformProperties propriedades, Clock relogio) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(propriedades.readTimeout());
        this.configurado = propriedades.configurado();
        this.baseUrl = configurado ? propriedades.baseUrl() : "";
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor())
                .build();
        this.executor = executor;
        this.token = new TokenGeoconform(restClient, executor, propriedades, relogio);
    }

    /**
     * `POST /api/geoconform/v1/processos` (I-01). Repetir depois de um 401 não duplica: o 401 quer dizer que o pedido não
     * foi tratado. Um 409 `idu_duplicado` é o processo que já existe e conta como criado.
     */
    public ResultadoGeoconform<ProcessoGeoconform> criarProcesso(PedidoCriacaoProcessoGeoconform pedido,
                                                                 ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        if (!configurado) {
            return new ResultadoGeoconform<>(executor.naoConfigurado(SistemaExterno.GEOCONFORM, OPERACAO_CRIAR_PROCESSO,
                    "POST", CAMINHO_PROCESSOS, referencia), null, null);
        }
        TokenGeoconform.Autenticacao autenticacao = token.obter(null, referencia);
        if (autenticacao.token() == null) {
            return comoFalha(autenticacao.falha());
        }
        ResultadoGeoconform<ProcessoGeoconform> resultado = enviar(pedido, autenticacao.token(), referencia);
        if (naoAutorizado(resultado.chamada())) {
            autenticacao = token.obter(autenticacao.token(), referencia);
            if (autenticacao.token() == null) {
                return comoFalha(autenticacao.falha());
            }
            resultado = enviar(pedido, autenticacao.token(), referencia);
        }
        if (resultado.duplicado()) {
            log.warn("Processo já existe no GeoConform (idu_duplicado), tratado como criado — processoId={}",
                    referencia == null ? null : referencia.processoId());
        }
        return resultado;
    }

    /**
     * `PATCH /api/geoconform/v1/processos/{idus}/visibilidade` (I-04). Idempotente: repetir sobre um processo já no estado
     * pedido dá 200. Repete-se só depois de um 401, como na criação.
     */
    public ResultadoChamadaExterna<Void> alterarVisibilidade(String idus, PedidoVisibilidadeGeoconform pedido,
                                                             ReferenciaChamada referencia) {
        Objects.requireNonNull(idus, "idus");
        Objects.requireNonNull(pedido, "pedido");
        if (idus.isBlank()) {
            throw new IllegalArgumentException("idus vazio");
        }
        return enviarComToken(OPERACAO_ALTERAR_VISIBILIDADE, HttpMethod.PATCH, CAMINHO_VISIBILIDADE, idus, pedido,
                referencia);
    }

    /**
     * `POST /api/geoconform/v1/processos/{idus}/publicacao` (I-10), com o IDUS do processo da publicação final. O
     * processo passa a `publicado` no GeoConform. Repete-se só depois de um 401.
     */
    public ResultadoChamadaExterna<Void> registarPublicacao(String idus, PedidoPublicacaoGeoconform pedido,
                                                            ReferenciaChamada referencia) {
        Objects.requireNonNull(idus, "idus");
        Objects.requireNonNull(pedido, "pedido");
        if (idus.isBlank()) {
            throw new IllegalArgumentException("idus vazio");
        }
        return enviarComToken(OPERACAO_REGISTAR_PUBLICACAO, HttpMethod.POST, CAMINHO_PUBLICACAO, idus, pedido,
                referencia);
    }

    /**
     * `PUT /api/geoconform/v1/tentativas/{idusDinamica}/identificadores` (I-03), em lote. Repetir dá 200: o IDUS anterior
     * continua a resolver para o mesmo processo. Repete-se só depois de um 401.
     */
    public ResultadoChamadaExterna<Void> corrigirIdentificadores(PedidoCorrecaoIdentificadoresGeoconform pedido,
                                                                 ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        if (pedido.idusDinamica() == null || pedido.idusDinamica().isBlank()) {
            throw new IllegalArgumentException("idusDinamica vazio");
        }
        if (pedido.correspondencias().isEmpty()) {
            throw new IllegalArgumentException("correspondencias vazio");
        }
        return enviarComToken(OPERACAO_CORRIGIR_IDENTIFICADORES, HttpMethod.PUT, CAMINHO_IDENTIFICADORES,
                pedido.idusDinamica(), pedido, referencia);
    }

    private ResultadoChamadaExterna<Void> enviarComToken(String operacao, HttpMethod metodo, String caminho,
                                                         String variavel, Object pedido, ReferenciaChamada referencia) {
        if (!configurado) {
            return executor.naoConfigurado(SistemaExterno.GEOCONFORM, operacao, metodo.name(), caminho, referencia);
        }
        TokenGeoconform.Autenticacao autenticacao = token.obter(null, referencia);
        if (autenticacao.token() == null) {
            return semCorpo(autenticacao.falha());
        }
        ResultadoChamadaExterna<Void> resultado = enviarSemResposta(operacao, metodo, caminho, variavel, pedido,
                autenticacao.token(), referencia);
        if (naoAutorizado(resultado)) {
            autenticacao = token.obter(autenticacao.token(), referencia);
            if (autenticacao.token() == null) {
                return semCorpo(autenticacao.falha());
            }
            resultado = enviarSemResposta(operacao, metodo, caminho, variavel, pedido, autenticacao.token(), referencia);
        }
        return resultado;
    }

    /** O IDUS vai como variável do caminho (codificado pelo template), nunca concatenado. */
    private ResultadoChamadaExterna<Void> enviarSemResposta(String operacao, HttpMethod metodo, String caminho,
                                                            String variavel, Object pedido, String bearer,
                                                            ReferenciaChamada referencia) {
        String modelo = baseUrl + caminho;
        return executor.executar(SistemaExterno.GEOCONFORM, operacao, CIRCUITO, metodo.name(), modelo, referencia,
                () -> restClient.method(metodo).uri(modelo, variavel)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(pedido)
                        .retrieve()
                        .toBodilessEntity());
    }

    /** O corpo de erro e o `Location` leem-se aqui porque o executor, partilhado, não os guarda. */
    private ResultadoGeoconform<ProcessoGeoconform> enviar(PedidoCriacaoProcessoGeoconform pedido, String bearer,
                                                           ReferenciaChamada referencia) {
        String url = baseUrl + CAMINHO_PROCESSOS;
        AtomicReference<ErroGeoconform> erro = new AtomicReference<>();
        AtomicReference<String> location = new AtomicReference<>();
        ResultadoChamadaExterna<ProcessoGeoconform> chamada = executor.executar(SistemaExterno.GEOCONFORM,
                OPERACAO_CRIAR_PROCESSO, CIRCUITO, "POST", url, referencia, () -> {
                    try {
                        ResponseEntity<ProcessoGeoconform> resposta = restClient.post().uri(url)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(pedido)
                                .retrieve()
                                .toEntity(ProcessoGeoconform.class);
                        location.set(resposta.getHeaders().getFirst(HttpHeaders.LOCATION));
                        return resposta;
                    } catch (RestClientResponseException e) {
                        erro.set(lerErro(e));
                        throw e;
                    }
                });
        return new ResultadoGeoconform<>(chamada, erro.get(), location.get());
    }

    private static ErroGeoconform lerErro(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErroGeoconform.class);
        } catch (RuntimeException ilegivel) {
            return null;
        }
    }

    private static boolean naoAutorizado(ResultadoChamadaExterna<?> chamada) {
        return chamada.falhaTecnica() == FalhaTecnicaExterna.ERRO_HTTP
                && Integer.valueOf(NAO_AUTORIZADO).equals(chamada.httpStatus());
    }

    private static ResultadoChamadaExterna<Void> semCorpo(ResultadoChamadaExterna<?> falha) {
        return new ResultadoChamadaExterna<>(falha.httpStatus(), null, falha.falhaTecnica(), falha.duracaoMs());
    }

    private static ResultadoGeoconform<ProcessoGeoconform> comoFalha(ResultadoChamadaExterna<?> falha) {
        return new ResultadoGeoconform<>(new ResultadoChamadaExterna<>(falha.httpStatus(), null, falha.falhaTecnica(),
                falha.duracaoMs()), null, null);
    }
}
