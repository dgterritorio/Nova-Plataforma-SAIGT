package com.conkord.saigt.integracao.snit;

import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.PropagacaoCorrelationIdInterceptor;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * Único ponto do `saigt-api` com HTTP ao SNIT (`contracts/snit.yaml`). As consultas lançam
 * `SnitIndisponivelException`; os registos (publicação, depósito e ficheiros do depósito) são escritas com circuito próprio e devolve a falha técnica no
 * resultado, para quem regista decidir o alerta e o reenvio. Os valores vindos do utilizador entram na query como
 * variáveis do URI template, nunca concatenados.
 */
@Component
@EnableConfigurationProperties(SnitProperties.class)
public class ClienteSnit {

    public static final String CIRCUITO_CONSULTA = "snit-consulta";
    public static final String CIRCUITO_REGISTO = "snit-registo";
    private static final String CABECALHO_CHAVE = "X-Saigt-Api-Key";
    private static final String CAMINHO_INSTRUMENTOS = "/api/integrations/saigt/instruments";
    private static final String CAMINHO_PUBLICACOES = "/api/integrations/saigt/publications";
    private static final String OPERACAO_REGISTAR_PUBLICACAO = "REGISTAR_PUBLICACAO";
    private static final String CAMINHO_DEPOSITOS = "/api/integrations/saigt/deposits";
    private static final String CAMINHO_FICHEIROS_DEPOSITO = CAMINHO_DEPOSITOS + "/{ssaigtId}/files";
    private static final String CAMINHO_DEPOSITO = CAMINHO_DEPOSITOS + "/{ssaigtId}";
    private static final String OPERACAO_REGISTAR_DEPOSITO = "REGISTAR_DEPOSITO";
    private static final String OPERACAO_REGISTAR_FICHEIROS_DEPOSITO = "REGISTAR_FICHEIROS_DEPOSITO";
    private static final String OPERACAO_ATUALIZAR_IDUS_DEPOSITO = "ATUALIZAR_IDUS_DEPOSITO";

    private final RestClient restClient;
    private final ExecutorChamadasExternas executor;
    private final boolean configurado;
    private final String baseUrl;

    public ClienteSnit(RestClient.Builder restClientBuilder, ExecutorChamadasExternas executor,
                       SnitProperties propriedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propriedades.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(propriedades.readTimeout());
        this.configurado = propriedades.baseUrl() != null && !propriedades.baseUrl().isBlank();
        this.baseUrl = configurado ? propriedades.baseUrl() : "";
        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new PropagacaoCorrelationIdInterceptor());
        if (propriedades.apiKey() != null && !propriedades.apiKey().isBlank()) {
            builder.defaultHeader(CABECALHO_CHAVE, propriedades.apiKey());
        }
        this.restClient = builder.build();
        this.executor = executor;
    }

    /** `pagina` conta a partir de 1, como no SNIT; só os IGT em vigor. */
    public PaginaSnit pesquisar(String tipo, String municipio, int pagina, int tamanho) {
        PaginaSnit resultado = executar("PESQUISAR_INSTRUMENTOS", CAMINHO_INSTRUMENTOS, () -> {
            Map<String, Object> variaveis = new HashMap<>();
            variaveis.put("status", IgtSnit.EM_VIGOR);
            variaveis.put("page", pagina);
            variaveis.put("pageSize", tamanho);
            return restClient.get().uri(uri -> {
                UriBuilder construtor = uri.path(CAMINHO_INSTRUMENTOS)
                        .queryParam("status", "{status}")
                        .queryParam("page", "{page}")
                        .queryParam("pageSize", "{pageSize}");
                if (tipo != null && !tipo.isBlank()) {
                    construtor.queryParam("type", "{type}");
                    variaveis.put("type", tipo);
                }
                if (municipio != null && !municipio.isBlank()) {
                    construtor.queryParam("municipality", "{municipality}");
                    variaveis.put("municipality", municipio);
                }
                return construtor.build(variaveis);
            }).retrieve().toEntity(PaginaSnit.class);
        }).corpo();
        if (resultado == null || resultado.items() == null) {
            throw new SnitIndisponivelException("Resposta do SNIT inválida.");
        }
        return resultado;
    }

    /** Vazio quando o SNIT responde 404; um 404 não conta como falha do circuito. */
    public Optional<IgtSnit> obter(long idIgt) {
        ResultadoChamadaExterna<IgtSnit> resultado = executar("OBTER_INSTRUMENTO", CAMINHO_INSTRUMENTOS + "/" + idIgt,
                () -> {
                    try {
                        return restClient.get().uri(CAMINHO_INSTRUMENTOS + "/{idIgt}", idIgt).retrieve()
                                .toEntity(IgtSnit.class);
                    } catch (HttpClientErrorException.NotFound e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    }
                });
        if (resultado.httpStatus() == HttpStatus.NOT_FOUND.value()) {
            return Optional.empty();
        }
        if (resultado.corpo() == null) {
            throw new SnitIndisponivelException("Resposta do SNIT inválida.");
        }
        return Optional.of(resultado.corpo());
    }

    public List<JsonNode> obterTabelaReferencia(ListaReferenciaSnit lista) {
        JsonNode corpo = executar(lista.operacao(), lista.caminho(),
                () -> restClient.get().uri(lista.caminho()).retrieve().toEntity(JsonNode.class)).corpo();
        if (corpo == null || !corpo.isArray()) {
            throw new SnitIndisponivelException("Resposta do SNIT inválida.");
        }
        return StreamSupport.stream(corpo.spliterator(), false).toList();
    }

    /**
     * Sem nova tentativa: o SNIT só garante a idempotência com o mesmo diploma. Os 4xx (incluindo o 409 de outro
     * diploma) não contam para o circuito `snit-registo` (`ignore-exceptions` em `application.properties`).
     */
    public ResultadoChamadaExterna<PublicacaoRecebidaSnit> registarPublicacao(PublicacaoSnit pedido,
                                                                              ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        if (!configurado) {
            return executor.naoConfigurado(SistemaExterno.SNIT, OPERACAO_REGISTAR_PUBLICACAO, HttpMethod.POST.name(),
                    CAMINHO_PUBLICACOES, referencia);
        }
        return executor.executar(SistemaExterno.SNIT, OPERACAO_REGISTAR_PUBLICACAO, CIRCUITO_REGISTO,
                HttpMethod.POST.name(), baseUrl + CAMINHO_PUBLICACOES, referencia,
                () -> restClient.post()
                        .uri(CAMINHO_PUBLICACOES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(pedido)
                        .retrieve()
                        .toEntity(PublicacaoRecebidaSnit.class));
    }

    /**
     * Sem nova tentativa, como a publicação. O 404 (publicação desconhecida no SNIT) e o 409 (`depositNumber`
     * duplicado) chegam como `ERRO_HTTP` e não contam para o circuito.
     */
    public ResultadoChamadaExterna<DepositoRecebidoSnit> registarDeposito(DepositoSnit pedido,
                                                                          ReferenciaChamada referencia) {
        Objects.requireNonNull(pedido, "pedido");
        return registar(OPERACAO_REGISTAR_DEPOSITO, CAMINHO_DEPOSITOS, referencia,
                () -> restClient.post()
                        .uri(CAMINHO_DEPOSITOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(pedido)
                        .retrieve()
                        .toEntity(DepositoRecebidoSnit.class));
    }

    /** Upsert por `fileId` no SNIT: reenviar o mesmo ficheiro não o duplica. */
    public ResultadoChamadaExterna<FicheirosRecebidosSnit> registarFicheirosDeposito(
            long ssaigtId, List<FicheiroDepositoSnit> ficheiros, ReferenciaChamada referencia) {
        Objects.requireNonNull(ficheiros, "ficheiros");
        String caminho = CAMINHO_DEPOSITOS + "/" + ssaigtId + "/files";
        return registar(OPERACAO_REGISTAR_FICHEIROS_DEPOSITO, caminho, referencia,
                () -> restClient.post()
                        .uri(CAMINHO_FICHEIROS_DEPOSITO, ssaigtId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new FicheirosDeposito(ficheiros))
                        .retrieve()
                        .toEntity(FicheirosRecebidosSnit.class));
    }

    /**
     * IDUS da Dinâmica no depósito do processo da publicação final. Com o processo já iniciado no SNIT, o `PUT` só
     * aceita o `idus`: outro campo no corpo dá 409. O 404 e o 409 chegam como `ERRO_HTTP` e não contam para o circuito.
     */
    public ResultadoChamadaExterna<DepositoRecebidoSnit> atualizarIdusDeposito(long ssaigtId, String idusDinamica,
                                                                               ReferenciaChamada referencia) {
        Objects.requireNonNull(idusDinamica, "idusDinamica");
        String caminho = CAMINHO_DEPOSITOS + "/" + ssaigtId;
        return registar(OPERACAO_ATUALIZAR_IDUS_DEPOSITO, HttpMethod.PUT, caminho, referencia,
                () -> restClient.put()
                        .uri(CAMINHO_DEPOSITO, ssaigtId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new IdusDeposito(idusDinamica))
                        .retrieve()
                        .toEntity(DepositoRecebidoSnit.class));
    }

    private <T> ResultadoChamadaExterna<T> registar(String operacao, String caminho, ReferenciaChamada referencia,
                                                    Supplier<ResponseEntity<T>> chamada) {
        return registar(operacao, HttpMethod.POST, caminho, referencia, chamada);
    }

    private <T> ResultadoChamadaExterna<T> registar(String operacao, HttpMethod metodo, String caminho,
                                                    ReferenciaChamada referencia, Supplier<ResponseEntity<T>> chamada) {
        if (!configurado) {
            return executor.naoConfigurado(SistemaExterno.SNIT, operacao, metodo.name(), caminho, referencia);
        }
        return executor.executar(SistemaExterno.SNIT, operacao, CIRCUITO_REGISTO, metodo.name(),
                baseUrl + caminho, referencia, chamada);
    }

    private record FicheirosDeposito(List<FicheiroDepositoSnit> files) {
    }

    private record IdusDeposito(String idus) {
    }

    private <T> ResultadoChamadaExterna<T> executar(String operacao, String caminho, Supplier<ResponseEntity<T>> chamada) {
        if (!configurado) {
            executor.naoConfigurado(SistemaExterno.SNIT, operacao, HttpMethod.GET.name(), caminho,
                    ReferenciaChamada.NENHUMA);
            throw new SnitIndisponivelException("Serviço do SNIT indisponível.");
        }
        ResultadoChamadaExterna<T> resultado = executor.executar(SistemaExterno.SNIT, operacao, CIRCUITO_CONSULTA,
                HttpMethod.GET.name(), baseUrl + caminho, ReferenciaChamada.NENHUMA, chamada);
        if (resultado.falhaTecnica() == FalhaTecnicaExterna.CIRCUITO_ABERTO) {
            throw new SnitIndisponivelException("Serviço do SNIT temporariamente indisponível.");
        }
        if (resultado.temFalhaTecnica()) {
            throw new SnitIndisponivelException("Serviço do SNIT indisponível. Tente novamente mais tarde.");
        }
        return resultado;
    }
}
