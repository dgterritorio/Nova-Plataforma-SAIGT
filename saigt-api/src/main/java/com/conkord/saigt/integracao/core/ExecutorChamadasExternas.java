package com.conkord.saigt.integracao.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class ExecutorChamadasExternas {

    private static final Logger log = LoggerFactory.getLogger(ExecutorChamadasExternas.class);
    private static final String MASCARA = "***";
    private static final Set<String> CABECALHOS_RESPOSTA_SENSIVEIS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT), HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT),
            HttpHeaders.PROXY_AUTHORIZATION.toLowerCase(Locale.ROOT));
    // Com o JdkClientHttpRequestFactory o timeout chega como TimeoutException embrulhado.
    private static final List<Class<? extends Throwable>> CAUSAS_TIMEOUT =
            List.of(SocketTimeoutException.class, HttpTimeoutException.class, TimeoutException.class);
    private static final Pattern UTILIZADOR_NO_URL = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*://)[^/?#]*@");
    static final int MAXIMO_CARACTERES_RESPOSTA = 4096;
    private static final JsonMapper JSON_LOG = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    // Os nomes repetem as constantes CIRCUITO* dos clientes para o core não depender de integracao/{sistema}.
    private static final Map<String, SistemaExterno> SISTEMA_POR_CIRCUITO = Map.of(
            "incm-autenticacao", SistemaExterno.INCM,
            "incm-submissao", SistemaExterno.INCM,
            "snit-consulta", SistemaExterno.SNIT,
            "snit-registo", SistemaExterno.SNIT,
            "pcgt", SistemaExterno.PCGT,
            "geoconform", SistemaExterno.GEOCONFORM);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ApplicationEventPublisher publicador;

    public ExecutorChamadasExternas(CircuitBreakerRegistry circuitBreakerRegistry,
                                    ApplicationEventPublisher publicador) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.publicador = publicador;
        // Os circuitos das properties já existem; os outros são criados na primeira chamada.
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registarTransicoes);
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(evento -> registarTransicoes(evento.getAddedEntry()));
    }

    private void registarTransicoes(CircuitBreaker circuito) {
        circuito.getEventPublisher().onStateTransition(evento -> {
            log.atWarn()
                    .addKeyValue("circuito", evento.getCircuitBreakerName())
                    .addKeyValue("estadoOrigem", evento.getStateTransition().getFromState().name())
                    .addKeyValue("estadoDestino", evento.getStateTransition().getToState().name())
                    .log("Circuit Breaker '{}' mudou de estado: {} -> {}", evento.getCircuitBreakerName(),
                            evento.getStateTransition().getFromState(), evento.getStateTransition().getToState());
            // HALF_OPEN -> OPEN é a mesma indisponibilidade: só a primeira abertura gera alerta.
            SistemaExterno sistema = SISTEMA_POR_CIRCUITO.get(evento.getCircuitBreakerName());
            if (evento.getStateTransition() == CircuitBreaker.StateTransition.CLOSED_TO_OPEN && sistema != null) {
                publicarCircuitoAberto(new CircuitoAbertoEvento(sistema, evento.getCircuitBreakerName(),
                        evento.getCreationTime().toInstant()));
            }
        });
    }

    /** Corre dentro da chamada que abriu o circuito: uma falha aqui nunca pode mudar o resultado dessa chamada. */
    private void publicarCircuitoAberto(CircuitoAbertoEvento evento) {
        try {
            publicador.publishEvent(evento);
        } catch (RuntimeException e) {
            log.error("Falha a publicar a abertura do circuito | sistema={} circuito={}", evento.sistema(),
                    evento.circuito(), e);
        }
    }

    /**
     * O Circuit Breaker só conta a falha se a exceção sair de dentro dele; uma resposta que a `chamada` devolva sem
     * exceção (por exemplo, um 404 esperado) conta como sucesso. `url` vai para o log sem a query string nem
     * credenciais; os corpos nunca vão.
     */
    public <T> ResultadoChamadaExterna<T> executar(SistemaExterno sistema, String operacao, String circuito,
                                                   String metodo, String url, ReferenciaChamada referencia,
                                                   Supplier<ResponseEntity<T>> chamada) {
        return executar(sistema, operacao, circuito, metodo, url, referencia, false, chamada);
    }

    /** `registarResposta` põe o corpo da resposta no log: nunca em operações que devolvam tokens ou dados pessoais. */
    public <T> ResultadoChamadaExterna<T> executar(SistemaExterno sistema, String operacao, String circuito,
                                                   String metodo, String url, ReferenciaChamada referencia,
                                                   boolean registarResposta, Supplier<ResponseEntity<T>> chamada) {
        Instant enviadoEm = Instant.now();
        ResultadoChamadaExterna<T> resultado;
        HttpHeaders cabecalhosResposta = null;
        Object corpoResposta = null;
        try {
            ResponseEntity<T> resposta = circuitBreakerRegistry.circuitBreaker(circuito).executeSupplier(chamada);
            cabecalhosResposta = resposta.getHeaders();
            corpoResposta = resposta.getBody();
            T corpo = resposta.getStatusCode().is2xxSuccessful() ? resposta.getBody() : null;
            resultado = ResultadoChamadaExterna.sucesso(resposta.getStatusCode().value(), corpo, duracaoMs(enviadoEm));
        } catch (CallNotPermittedException e) {
            resultado = ResultadoChamadaExterna.falha(FalhaTecnicaExterna.CIRCUITO_ABERTO, null, duracaoMs(enviadoEm));
        } catch (RestClientResponseException e) {
            cabecalhosResposta = e.getResponseHeaders();
            corpoResposta = e.getResponseBodyAsString();
            resultado = ResultadoChamadaExterna.falha(FalhaTecnicaExterna.ERRO_HTTP, e.getStatusCode().value(),
                    duracaoMs(enviadoEm));
        } catch (RestClientException e) {
            resultado = ResultadoChamadaExterna.falha(classificar(e), null, duracaoMs(enviadoEm));
        }
        registarChamada(sistema, operacao, metodo, url, referencia, enviadoEm, cabecalhosResposta,
                registarResposta ? corpoResposta : null, resultado);
        return resultado;
    }

    /** Sistema sem URL base configurado: a chamada não é tentada e não conta no circuito. */
    public <T> ResultadoChamadaExterna<T> naoConfigurado(SistemaExterno sistema, String operacao, String metodo,
                                                         String url, ReferenciaChamada referencia) {
        ResultadoChamadaExterna<T> resultado = ResultadoChamadaExterna.falha(FalhaTecnicaExterna.NAO_CONFIGURADO, null, 0);
        registarChamada(sistema, operacao, metodo, url, referencia, Instant.now(), null, null, resultado);
        return resultado;
    }

    private static FalhaTecnicaExterna classificar(RestClientException e) {
        if (temCausa(e, CAUSAS_TIMEOUT)) {
            return FalhaTecnicaExterna.TIMEOUT;
        }
        if (e instanceof ResourceAccessException) {
            return FalhaTecnicaExterna.ERRO_REDE;
        }
        // Corpo 2xx ilegível conta como ERRO_HTTP; outra IOException é a ligação perdida a meio do corpo.
        if (temCausa(e, List.of(HttpMessageNotReadableException.class)) || !temCausa(e, List.of(IOException.class))) {
            return FalhaTecnicaExterna.ERRO_HTTP;
        }
        return FalhaTecnicaExterna.ERRO_REDE;
    }

    private static boolean temCausa(Throwable erro, List<Class<? extends Throwable>> tipos) {
        return Stream.<Throwable>iterate(erro, Objects::nonNull, Throwable::getCause)
                .anyMatch(causa -> tipos.stream().anyMatch(tipo -> tipo.isInstance(causa)));
    }

    private static void registarChamada(SistemaExterno sistema, String operacao, String metodo, String url,
                                        ReferenciaChamada referencia, Instant enviadoEm, HttpHeaders cabecalhosResposta,
                                        Object corpoResposta, ResultadoChamadaExterna<?> resultado) {
        ReferenciaChamada ref = referencia == null ? ReferenciaChamada.NENHUMA : referencia;
        String desfecho = resultado.temFalhaTecnica() ? resultado.falhaTecnica().name() : "OK";
        int estadoHttp = resultado.httpStatus() == null ? 0 : resultado.httpStatus();
        LoggingEventBuilder evento = log.atLevel(nivel(resultado))
                .addKeyValue("sistema", sistema.name())
                .addKeyValue("operacao", operacao);
        if (ref.processoId() != null) {
            evento = evento.addKeyValue("processoId", ref.processoId().toString());
        }
        if (ref.idus() != null) {
            evento = evento.addKeyValue("idus", ref.idus());
        }
        if (ref.actIdentifier() != null) {
            evento = evento.addKeyValue("actIdentifier", ref.actIdentifier());
        }
        evento = evento.addKeyValue("metodo", metodo)
                .addKeyValue("url", urlParaLog(url))
                .addKeyValue("estadoHttp", estadoHttp)
                .addKeyValue("duracaoMs", resultado.duracaoMs())
                .addKeyValue("resultado", desfecho)
                .addKeyValue("enviadoEm", enviadoEm.toString())
                .addKeyValue("cabecalhosPedido", Map.of(sistema.cabecalhoCredencial(), MASCARA));
        if (cabecalhosResposta != null) {
            evento = evento.addKeyValue("cabecalhosResposta", mascarar(cabecalhosResposta));
        }
        if (corpoResposta != null) {
            evento = evento.addKeyValue("resposta", corpoRespostaParaLog(corpoResposta));
        }
        evento.log("Chamada externa {} {} -> {} ({})", sistema, operacao, desfecho, estadoHttp);
    }

    private static Level nivel(ResultadoChamadaExterna<?> resultado) {
        if (!resultado.temFalhaTecnica()) {
            return Level.INFO;
        }
        return resultado.falhaTecnica() == FalhaTecnicaExterna.CIRCUITO_ABERTO ? Level.WARN : Level.ERROR;
    }

    private static Map<String, List<String>> mascarar(HttpHeaders cabecalhos) {
        Map<String, List<String>> mascarados = new LinkedHashMap<>();
        cabecalhos.forEach((nome, valores) -> mascarados.put(nome,
                CABECALHOS_RESPOSTA_SENSIVEIS.contains(nome.toLowerCase(Locale.ROOT)) ? List.of(MASCARA) : valores));
        return mascarados;
    }

    private static String urlParaLog(String url) {
        if (url == null) {
            return null;
        }
        int fim = url.length();
        for (char separador : new char[] {'?', '#'}) {
            int indice = url.indexOf(separador);
            if (indice >= 0 && indice < fim) {
                fim = indice;
            }
        }
        return UTILIZADOR_NO_URL.matcher(url.substring(0, fim)).replaceFirst("$1");
    }

    /** Uma linha só, com o JSON compacto e no máximo `MAXIMO_CARACTERES_RESPOSTA` caracteres. */
    private static String corpoRespostaParaLog(Object corpo) {
        if (corpo == null || corpo instanceof String texto && texto.isBlank()) {
            return "-";
        }
        String json = corpo instanceof String texto ? compactar(texto) : serializar(corpo);
        return json.length() <= MAXIMO_CARACTERES_RESPOSTA ? json
                : json.substring(0, MAXIMO_CARACTERES_RESPOSTA) + "...[TRUNCADO " + json.length() + " caracteres]";
    }

    private static String compactar(String texto) {
        try {
            return JSON_LOG.writeValueAsString(JSON_LOG.readTree(texto));
        } catch (JsonProcessingException e) {
            return texto.replace("\r", "\\r").replace("\n", "\\n");
        }
    }

    private static String serializar(Object corpo) {
        try {
            return JSON_LOG.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            return "[corpo não serializável: " + corpo.getClass().getSimpleName() + "]";
        }
    }

    private static long duracaoMs(Instant inicio) {
        return Duration.between(inicio, Instant.now()).toMillis();
    }
}
