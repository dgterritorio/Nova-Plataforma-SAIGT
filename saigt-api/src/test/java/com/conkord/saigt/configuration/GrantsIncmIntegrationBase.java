package com.conkord.saigt.configuration;

import com.conkord.saigt.integracao.incm.AdesaoIncm;
import com.conkord.saigt.integracao.incm.IncmAutenticador;
import com.conkord.saigt.integracao.incm.ResultadoAutenticacaoIncm;
import com.conkord.saigt.integracao.incm.UtilizadorIncm;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

/** Os dois grants de utilizador por HTTP, com a INCM mockada na fronteira e o resto real (H2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + GrantsIncmIntegrationBase.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + GrantsIncmIntegrationBase.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "resilience4j.ratelimiter.configs.auth.limit-for-period=1000"
})
@ActiveProfiles("test")
@Import(GrantsIncmIntegrationBase.Nio2TomcatConfig.class)
abstract class GrantsIncmIntegrationBase {

    static final int PORTA = 18094;
    static final String GRANT_INCM = "urn:saigt:params:oauth:grant-type:incm-password";
    static final String GRANT_ENTIDADE = "urn:saigt:params:oauth:grant-type:entidade";
    static final String PASSWORD_FICTICIA = "senha-ficticia-de-teste";

    /** NIO2 evita o mecanismo de wakeup por Pipe do conector NIO padrão do Tomcat, que falha neste ambiente sandboxed. */
    @TestConfiguration
    static class Nio2TomcatConfig {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Customizer() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }

    @MockBean
    protected IncmAutenticador incm;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UtilizadorRepository utilizadorRepository;

    @Autowired
    protected EntidadeRepository entidadeRepository;

    @Autowired
    protected SessaoRepository sessaoRepository;

    @Autowired
    protected AuditoriaRepository auditoriaRepository;

    protected final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    protected static String emailUnico() {
        return "utilizador-" + UUID.randomUUID() + "@teste.invalid";
    }

    protected static AdesaoIncm adesao(String enrollmentId, String nipc, String nome) {
        return new AdesaoIncm(nipc, nome, null, "210000000", "geral@teste.invalid", "Responsável Fictício",
                "responsavel@teste.invalid", "Diretor", enrollmentId, 1234567891234567L, "C", null, List.of(),
                List.of(new AdesaoIncm.AtoAutorizado(9L, "Despacho", true)), null);
    }

    protected void incmAceita(String email, String nome, AdesaoIncm... adesoes) {
        when(incm.autenticarComMotivo(email, PASSWORD_FICTICIA)).thenReturn(ResultadoAutenticacaoIncm.aceite(
                new UtilizadorIncm(nome, email, "Técnica", List.of(adesoes))));
    }

    protected Entidade entidade(String nipc, String descricao, TipoEntidade tipo, String dico, String codigo) {
        Entidade entidade = new Entidade(codigo, tipo, nipc, descricao, descricao);
        entidade.setDico(dico);
        return entidadeRepository.saveAndFlush(entidade);
    }

    protected Utilizador contaIncm(String email, String nome) {
        return utilizadorRepository.saveAndFlush(new Utilizador(nome, email, "INCM"));
    }

    protected HttpResponse<String> primeiroPasso(String email, String password) {
        return http.pedirToken("", null, null, form(Map.of("grant_type", GRANT_INCM, "client_id", "saigt-ui",
                "username", email, "password", password)));
    }

    protected HttpResponse<String> segundoPasso(String preAuthToken, String incmEnrollmentId) {
        return http.pedirToken("", null, null, form(Map.of("grant_type", GRANT_ENTIDADE, "client_id", "saigt-ui",
                "pre_auth_token", preAuthToken, "incm_enrollment_id", incmEnrollmentId)));
    }

    protected JsonNode json(HttpResponse<String> resposta) throws Exception {
        return objectMapper.readTree(resposta.body());
    }

    protected String preAuthToken(String email) throws Exception {
        HttpResponse<String> resposta = primeiroPasso(email, PASSWORD_FICTICIA);
        if (resposta.statusCode() != 200) {
            throw new IllegalStateException("Primeiro passo falhou: " + resposta.statusCode() + " " + resposta.body());
        }
        return json(resposta).get("access_token").asText();
    }

    protected static String form(Map<String, String> campos) {
        Map<String, String> ordenados = new LinkedHashMap<>(campos);
        return ordenados.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
