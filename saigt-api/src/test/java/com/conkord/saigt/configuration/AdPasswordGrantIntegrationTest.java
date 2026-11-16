package com.conkord.saigt.configuration;

import com.conkord.saigt.exception.AdIndisponivelException;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.login.LdapAutenticador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.configuration.GrantsIncmIntegrationBase.form;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Grant `ad-password` por HTTP, com o AD mockado na fronteira ({@link LdapAutenticador}) e o resto real (H2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + AdPasswordGrantIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + AdPasswordGrantIntegrationTest.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "resilience4j.ratelimiter.configs.auth.limit-for-period=1000"
})
@ActiveProfiles("test")
@Import(GrantsIncmIntegrationBase.Nio2TomcatConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AdPasswordGrantIntegrationTest {

    static final int PORTA = 18096;
    private static final String GRANT_AD = "urn:saigt:params:oauth:grant-type:ad-password";
    private static final String SENHA = "senha-ficticia-de-teste";

    @MockBean
    private LdapAutenticador ldap;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    private static String usernameUnico() {
        return "dgt." + UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpResponse<String> grant(String username, String password) {
        return http.pedirToken("", null, null, form(Map.of("grant_type", GRANT_AD, "client_id", "saigt-ui",
                "username", username, "password", password)));
    }

    private JsonNode json(HttpResponse<String> resposta) throws Exception {
        return objectMapper.readTree(resposta.body());
    }

    private Utilizador contaDgt(String username, PerfilInterno perfil) {
        Utilizador conta = new Utilizador(username, null);
        conta.setPerfilInterno(perfil);
        return utilizadorRepository.saveAndFlush(conta);
    }

    private List<Auditoria> auditoriaDe(Utilizador conta) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", conta.getId().toString());
    }

    private long ultimaAuditoria() {
        return auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc().stream()
                .mapToLong(Auditoria::getId).max().orElse(0L);
    }

    private List<Sessao> sessoesDe(Utilizador conta) {
        return sessaoRepository.findAll().stream()
                .filter(sessao -> sessao.getUtilizador().getId().equals(conta.getId()))
                .toList();
    }

    @Test
    void deveEmitirOsTokensComOrigemAdDgtEPerfilESemEntidadeCriandoAContaVisitanteNoPrimeiroLogin() throws Exception {
        when(ldap.confirmarAcesso("ana.silva", SENHA)).thenReturn(true);

        HttpResponse<String> resposta = grant("ana.silva", SENHA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Cache-Control")).contains("no-store");
        JsonNode corpo = json(resposta);
        assertThat(corpo.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expires_in").asLong()).isEqualTo(900L);
        assertThat(corpo.get("refresh_token").asText()).isNotBlank();

        JWTClaimsSet claims = SignedJWT.parse(corpo.get("access_token").asText()).getJWTClaimsSet();
        Utilizador conta = utilizadorRepository.findByNomeAndOrigemAutenticacao("ana.silva", "AD_DGT")
                .orElseThrow();
        assertThat(claims.getSubject()).isEqualTo(conta.getId().toString());
        assertThat(claims.getIssuer()).isNotNull();
        assertThat(claims.getAudience()).containsExactly("saigt-api");
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getStringClaim("name")).isEqualTo("ana.silva");
        assertThat(claims.getStringClaim("origem")).isEqualTo("AD_DGT");
        assertThat(claims.getStringClaim("perfil_interno")).isEqualTo("VISITANTE");
        assertThat(claims.getClaims()).doesNotContainKeys("entidade", "atos_autorizados", "email", "cargo");

        assertThat(conta.getPerfilInterno()).isEqualTo(PerfilInterno.VISITANTE);
        assertThat(conta.getEmail()).isNull();
        assertThat(auditoriaDe(conta)).extracting(Auditoria::getAcao).containsExactlyInAnyOrder("CRIAR", "LOGIN");
        Auditoria login = auditoriaDe(conta).stream().filter(a -> a.getAcao().equals("LOGIN")).findFirst().orElseThrow();
        assertThat(login.getUtilizadorId()).isEqualTo(conta.getId());
        assertThat(login.getEntidadeId()).isNull();

        JWTClaimsSet refresh = SignedJWT.parse(corpo.get("refresh_token").asText()).getJWTClaimsSet();
        assertThat(refresh.getAudience()).containsExactly("saigt-api-refresh");
        Sessao sessao = sessaoRepository.findById(refresh.getJWTID()).orElseThrow();
        assertThat(sessao.getTipo()).isEqualTo("ATIVA");
        assertThat(sessao.getRevogadoEm()).isNull();
        assertThat(sessao.getContextoSessao()).isNull();
    }

    @Test
    void deveManterOEmailOTelefoneAMoradaEONomeDeUmaContaDgtQueVoltaAEntrar() throws Exception {
        String username = usernameUnico();
        Utilizador conta = contaDgt(username, PerfilInterno.MASTER);
        conta.setEmail(username + "@alterado.invalid");
        conta.setTelefone("910000000");
        conta.setMorada("Rua do Perfil, 1");
        utilizadorRepository.saveAndFlush(conta);
        when(ldap.confirmarAcesso(username, SENHA)).thenReturn(true);

        HttpResponse<String> resposta = grant(username, SENHA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        Utilizador depois = utilizadorRepository.findById(conta.getId()).orElseThrow();
        assertThat(depois.getNome()).isEqualTo(username);
        assertThat(depois.getEmail()).isEqualTo(username + "@alterado.invalid");
        assertThat(depois.getTelefone()).isEqualTo("910000000");
        assertThat(depois.getMorada()).isEqualTo("Rua do Perfil, 1");
        JWTClaimsSet claims = SignedJWT.parse(json(resposta).get("access_token").asText()).getJWTClaimsSet();
        assertThat(claims.getStringClaim("email")).isEqualTo(username + "@alterado.invalid");
        assertThat(claims.getStringClaim("perfil_interno")).isEqualTo("MASTER");
        assertThat(auditoriaDe(conta)).extracting(Auditoria::getAcao).containsExactly("LOGIN");
    }

    @Test
    void deveRecusarAPasswordErradaAuditandoComOUtilizadorEOUsernameSemContaSemAuditoria() throws Exception {
        String username = usernameUnico();
        Utilizador conta = contaDgt(username, PerfilInterno.TECNICO);

        HttpResponse<String> errada = grant(username, "senha-errada");

        assertThat(errada.statusCode()).isEqualTo(400);
        assertThat(json(errada).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(errada).get("error_description").asText()).isEqualTo("Credenciais inválidas.");
        List<Auditoria> registos = auditoriaDe(conta);
        assertThat(registos).hasSize(1);
        assertThat(registos.get(0).getAcao()).isEqualTo("LOGIN_RECUSADO");
        assertThat(registos.get(0).getUtilizadorId()).isEqualTo(conta.getId());
        assertThat(registos.get(0).getDadosAlteracao()).contains("CREDENCIAIS_RECUSADAS_PELO_AD");
        assertThat(sessoesDe(conta)).isEmpty();

        long auditoriasAntes = ultimaAuditoria();
        String semConta = usernameUnico();
        HttpResponse<String> desconhecido = grant(semConta, "senha-errada");

        assertThat(desconhecido.statusCode()).isEqualTo(400);
        assertThat(json(desconhecido).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(ultimaAuditoria()).isEqualTo(auditoriasAntes);
        assertThat(utilizadorRepository.findByNomeAndOrigemAutenticacao(semConta, "AD_DGT")).isEmpty();
    }

    @Test
    void deveDarServerErrorSemGravarNadaQuandoOAdEstaIndisponivel() throws Exception {
        String username = usernameUnico();
        when(ldap.confirmarAcesso(username, SENHA)).thenThrow(new AdIndisponivelException("AD da DGT indisponível."));
        long auditoriasAntes = ultimaAuditoria();

        HttpResponse<String> resposta = grant(username, SENHA);

        assertThat(resposta.statusCode()).isEqualTo(500);
        assertThat(json(resposta).get("error").asText()).isEqualTo("server_error");
        assertThat(json(resposta).get("error_description").asText())
                .isEqualTo("Serviço de autenticação da DGT temporariamente indisponível.");
        assertThat(utilizadorRepository.findByNomeAndOrigemAutenticacao(username, "AD_DGT")).isEmpty();
        assertThat(ultimaAuditoria()).isEqualTo(auditoriasAntes);
    }

    @Test
    void deveRecusarOUsernameDeUmaContaIncmSemOEnviarAoAd() throws Exception {
        String emailIncm = "incm-" + UUID.randomUUID() + "@teste.invalid";
        utilizadorRepository.saveAndFlush(new Utilizador("Conta INCM Fictícia", emailIncm, "INCM"));
        when(ldap.confirmarAcesso(any(), any())).thenReturn(true);

        HttpResponse<String> resposta = grant(emailIncm, SENHA);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(json(resposta).get("error").asText()).isEqualTo("invalid_grant");
        verify(ldap, never()).confirmarAcesso(emailIncm, SENHA);
        assertThat(utilizadorRepository.findByNomeAndOrigemAutenticacao(emailIncm, "AD_DGT")).isEmpty();
    }

    @Test
    void deveRevogarASessaoAtivaAnteriorEDeixarUmaSoAtiva() throws Exception {
        String username = usernameUnico();
        Utilizador conta = contaDgt(username, PerfilInterno.TECNICO);
        when(ldap.confirmarAcesso(username, SENHA)).thenReturn(true);

        String primeiro = json(grant(username, SENHA)).get("refresh_token").asText();
        String segundo = json(grant(username, SENHA)).get("refresh_token").asText();

        String jtiPrimeiro = SignedJWT.parse(primeiro).getJWTClaimsSet().getJWTID();
        String jtiSegundo = SignedJWT.parse(segundo).getJWTClaimsSet().getJWTID();
        assertThat(sessaoRepository.findById(jtiPrimeiro).orElseThrow().getRevogadoEm()).isNotNull();
        assertThat(sessaoRepository.findById(jtiSegundo).orElseThrow().getRevogadoEm()).isNull();
        assertThat(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(conta.getId(), "ATIVA"))
                .extracting(Sessao::getJti).containsExactly(jtiSegundo);
    }

    @Test
    void aSessaoDoAdRodaPeloRefreshSemEntidadeETerminaPelaRevogacao() throws Exception {
        String username = usernameUnico();
        Utilizador conta = contaDgt(username, PerfilInterno.TECNICO);
        when(ldap.confirmarAcesso(username, SENHA)).thenReturn(true);
        String refreshLogin = json(grant(username, SENHA)).get("refresh_token").asText();

        HttpResponse<String> refresh = http.postForm("/oauth2/token",
                form(Map.of("grant_type", "refresh_token", "client_id", "saigt-ui", "refresh_token", refreshLogin)));

        assertThat(refresh.statusCode()).isEqualTo(200);
        JsonNode corpo = json(refresh);
        JWTClaimsSet acesso = SignedJWT.parse(corpo.get("access_token").asText()).getJWTClaimsSet();
        assertThat(acesso.getStringClaim("origem")).isEqualTo("AD_DGT");
        assertThat(acesso.getClaim("entidade")).isNull();
        String refreshNovo = corpo.get("refresh_token").asText();
        String jtiNovo = SignedJWT.parse(refreshNovo).getJWTClaimsSet().getJWTID();
        assertThat(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(conta.getId(), "ATIVA"))
                .extracting(Sessao::getJti).containsExactly(jtiNovo);

        assertThat(http.postForm("/oauth2/revoke", form(Map.of("client_id", "saigt-ui", "token", refreshNovo)))
                .statusCode()).isEqualTo(200);
        assertThat(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(conta.getId(), "ATIVA")).isEmpty();
    }

    @Test
    void deveDarInvalidRequestComCamposEmFalta() throws Exception {
        HttpResponse<String> semPassword = http.pedirToken("", null, null, form(Map.of("grant_type", GRANT_AD,
                "client_id", "saigt-ui", "username", "ana.silva")));
        HttpResponse<String> semUsername = http.pedirToken("", null, null, form(Map.of("grant_type", GRANT_AD,
                "client_id", "saigt-ui", "password", SENHA)));

        assertThat(semPassword.statusCode()).isEqualTo(400);
        assertThat(json(semPassword).get("error").asText()).isEqualTo("invalid_request");
        assertThat(semUsername.statusCode()).isEqualTo(400);
        assertThat(json(semUsername).get("error").asText()).isEqualTo("invalid_request");
    }

    @Test
    void osLogsDoGrantNaoTemOUsernameNemAPassword(CapturedOutput saida) throws Exception {
        String certo = usernameUnico();
        contaDgt(certo, PerfilInterno.TECNICO);
        when(ldap.confirmarAcesso(certo, SENHA)).thenReturn(true);
        String semConta = usernameUnico();

        assertThat(grant(certo, SENHA).statusCode()).isEqualTo(200);
        assertThat(grant(certo, "senha-errada").statusCode()).isEqualTo(400);
        assertThat(grant(semConta, "senha-errada").statusCode()).isEqualTo(400);

        assertThat(saida.getAll()).contains("Login AD concluído", "Login AD recusado")
                .doesNotContain(certo, semConta, SENHA, "senha-errada");
    }
}
