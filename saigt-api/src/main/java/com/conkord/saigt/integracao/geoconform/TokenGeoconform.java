package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token OAuth 2.0 (client credentials, âmbito `processos:escrever`) do GeoConform, só em memória: nunca vai para a base
 * de dados, ficheiro ou log. Renova-se 30 s antes de expirar. Um pedido de cada vez: quem espera reutiliza o token que o
 * outro obteve.
 */
final class TokenGeoconform {

    static final String OPERACAO = "TOKEN";
    static final String AMBITO = "processos:escrever";
    static final Duration ANTECEDENCIA_RENOVACAO = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(TokenGeoconform.class);

    /** `token` nulo quando o pedido falhou; `falha` traz então o resultado desse pedido. */
    record Autenticacao(String token, ResultadoChamadaExterna<?> falha) {

        @Override
        public String toString() {
            return "Autenticacao[token=***, falha=" + falha + "]";
        }
    }

    private record TokenEmMemoria(String valor, Instant expiraEm) {

        @Override
        public String toString() {
            return "TokenEmMemoria[valor=***, expiraEm=" + expiraEm + "]";
        }
    }

    private final RestClient restClient;
    private final ExecutorChamadasExternas executor;
    private final GeoconformProperties propriedades;
    private final Clock relogio;
    private final ReentrantLock bloqueio = new ReentrantLock();
    private volatile TokenEmMemoria atual;

    TokenGeoconform(RestClient restClient, ExecutorChamadasExternas executor, GeoconformProperties propriedades,
                    Clock relogio) {
        this.restClient = restClient;
        this.executor = executor;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * Devolve o token em memória enquanto faltarem mais de 30 s para expirar, a menos que seja `recusado` (o que o
     * GeoConform acabou de responder com 401); nesse caso, ou sem token, pede outro.
     */
    Autenticacao obter(String recusado, ReferenciaChamada referencia) {
        TokenEmMemoria token = atual;
        if (utilizavel(token, recusado)) {
            return new Autenticacao(token.valor(), null);
        }
        bloqueio.lock();
        try {
            token = atual;
            if (utilizavel(token, recusado)) {
                return new Autenticacao(token.valor(), null);
            }
            atual = null;
            return pedir(referencia);
        } finally {
            bloqueio.unlock();
        }
    }

    private boolean utilizavel(TokenEmMemoria token, String recusado) {
        return token != null && !token.valor().equals(recusado)
                && relogio.instant().isBefore(token.expiraEm().minus(ANTECEDENCIA_RENOVACAO));
    }

    private Autenticacao pedir(ReferenciaChamada referencia) {
        String url = propriedades.tokenUrl();
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "client_credentials");
        formulario.add("scope", AMBITO);
        ResultadoChamadaExterna<RespostaToken> chamada = executor.executar(SistemaExterno.GEOCONFORM, OPERACAO,
                ClienteGeoconform.CIRCUITO, "POST", url, referencia, () -> restClient.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, basic())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formulario)
                        .retrieve()
                        .toEntity(RespostaToken.class));
        if (chamada.temFalhaTecnica()) {
            log.error("Pedido do token do GeoConform falhou — processoId={} falha={} httpStatus={}",
                    processoId(referencia), chamada.falhaTecnica(), chamada.httpStatus());
            return new Autenticacao(null, chamada);
        }
        RespostaToken resposta = chamada.corpo();
        if (resposta == null || resposta.accessToken() == null || resposta.accessToken().isBlank()) {
            log.error("Resposta do token do GeoConform sem access_token — processoId={} httpStatus={}",
                    processoId(referencia), chamada.httpStatus());
            return new Autenticacao(null, new ResultadoChamadaExterna<>(chamada.httpStatus(), null,
                    FalhaTecnicaExterna.ERRO_HTTP, chamada.duracaoMs()));
        }
        // Sem expires_in serve só a operação que o pediu.
        Instant agora = relogio.instant();
        Instant expiraEm = resposta.expiresIn() == null ? agora : agora.plusSeconds(resposta.expiresIn());
        atual = new TokenEmMemoria(resposta.accessToken(), expiraEm);
        return new Autenticacao(resposta.accessToken(), null);
    }

    /** RFC 6749 §2.3.1: o cliente e o segredo vão codificados como formulário antes do Base64. */
    private String basic() {
        String credenciais = URLEncoder.encode(propriedades.clientId(), StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(propriedades.clientSecret(), StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }

    private static Object processoId(ReferenciaChamada referencia) {
        return referencia == null ? null : referencia.processoId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaToken(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("token_type") String tokenType,
                         @JsonProperty("expires_in") Long expiresIn) {

        @Override
        public String toString() {
            return "RespostaToken[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
}
