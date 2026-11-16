package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.core.JwtKeyProvider;
import com.conkord.saigt.service.core.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Grant `refresh_token` e revogação (RFC 7009) do `saigt-ui` por HTTP, sobre a tabela `sessao`. */
@ExtendWith(OutputCaptureExtension.class)
class RefreshERevogacaoIntegrationTest extends GrantsIncmIntegrationBase {

    private static final String ENROLLMENT = "ENR-396";

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    @Autowired
    private JwtKeyProvider keyProvider;

    private String nipcSintra;

    private JsonNode login(String email) throws Exception {
        if (nipcSintra == null) {
            nipcSintra = "5" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
            entidade(nipcSintra, "Câmara Fictícia " + nipcSintra, TipoEntidade.CM, "1111", "C-" + nipcSintra);
        }
        incmAceita(email, "Helena Fictícia", adesao(ENROLLMENT, nipcSintra, "Câmara Fictícia " + nipcSintra));
        HttpResponse<String> resposta = segundoPasso(preAuthToken(email), ENROLLMENT);
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        return json(resposta);
    }

    private HttpResponse<String> refresh(String refreshToken) {
        return http.pedirToken("", null, null, form(Map.of("grant_type", "refresh_token", "client_id", "saigt-ui",
                "refresh_token", refreshToken)));
    }

    private HttpResponse<String> revogar(String corpo) {
        return http.postForm("/oauth2/revoke", corpo);
    }

    private void assertErro(HttpResponse<String> resposta, int estado, String codigo) throws Exception {
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(estado);
        assertThat(json(resposta).get("error").asText()).isEqualTo(codigo);
        assertThat(json(resposta).has("access_token")).isFalse();
    }

    private static String jti(String jwt) throws Exception {
        return SignedJWT.parse(jwt).getJWTClaimsSet().getJWTID();
    }

    private List<Sessao> ativas(String email) {
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        return sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizador.getId(), "ATIVA");
    }

    @Test
    void oRefreshDaUmParNovoEORefreshAntigoReutilizadoDaInvalidGrant() throws Exception {
        String email = emailUnico();
        String refreshAntigo = login(email).get("refresh_token").asText();

        HttpResponse<String> resposta = refresh(refreshAntigo);

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        JsonNode corpo = json(resposta);
        assertThat(corpo.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expires_in").asLong()).isEqualTo(900L);
        String refreshNovo = corpo.get("refresh_token").asText();
        assertThat(refreshNovo).isNotEqualTo(refreshAntigo);
        assertThat(resposta.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(sessaoRepository.findById(jti(refreshAntigo)).orElseThrow().getRevogadoEm()).isNotNull();
        assertThat(ativas(email)).extracting(Sessao::getJti).containsExactly(jti(refreshNovo));
        assertThat(http.get("/api/v1/processos", corpo.get("access_token").asText()).statusCode()).isEqualTo(200);

        assertErro(refresh(refreshAntigo), 400, "invalid_grant");
        assertThat(ativas(email)).extracting(Sessao::getJti).containsExactly(jti(refreshNovo));
    }

    @Test
    void umTokenDePreAutenticacaoNuncaDaTokensPeloRefresh() throws Exception {
        String email = emailUnico();
        entidade("509396001", "Câmara Pré Fictícia", TipoEntidade.CM, "1112", "C-509396001");
        incmAceita(email, "Pedro Fictício", adesao("ENR-PRE", "509396001", "Câmara Pré Fictícia"));
        String preAuth = preAuthToken(email);
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        String forjado = refreshJwtComJti(utilizador.getId(), jti(preAuth));

        assertErro(refresh(preAuth), 400, "invalid_grant");
        assertErro(refresh(forjado), 400, "invalid_grant");

        assertThat(ativas(email)).isEmpty();
        Sessao pre = sessaoRepository.findById(jti(preAuth)).orElseThrow();
        assertThat(pre.getTipo()).isEqualTo("PRE_AUTENTICACAO");
        assertThat(pre.getRevogadoEm()).isNull();
    }

    @Test
    void aColunaJtiGuardaSoOJtiDoRefreshToken() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();
        String rodado = json(refresh(refreshToken)).get("refresh_token").asText();

        for (String token : List.of(refreshToken, rodado)) {
            Sessao linha = sessaoRepository.findById(jti(token)).orElseThrow();
            assertThat(linha.getJti()).isEqualTo(jti(token)).isNotEqualTo(token).doesNotContain(".");
            assertThat(SignedJWT.parse(token).getJWTClaimsSet().getAudience()).containsExactly("saigt-api-refresh");
        }
    }

    /** O fim da sessão é o `expira_em` gravado no login; o pedido depois dele é simulado recuando a linha atual. */
    @Test
    void asLinhasNovasHerdamOExpiraEmEDepoisDeleORefreshDaInvalidGrant() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();
        Instant fimSessao = sessaoRepository.findById(jti(refreshToken)).orElseThrow().getExpiraEm();

        for (int i = 0; i < 3; i++) {
            refreshToken = json(refresh(refreshToken)).get("refresh_token").asText();
            Sessao linha = sessaoRepository.findById(jti(refreshToken)).orElseThrow();
            assertThat(linha.getExpiraEm()).isEqualTo(fimSessao);
            assertThat(SignedJWT.parse(refreshToken).getJWTClaimsSet().getExpirationTime().toInstant())
                    .isBeforeOrEqualTo(fimSessao);
        }

        Sessao atual = sessaoRepository.findById(jti(refreshToken)).orElseThrow();
        atual.setExpiraEm(Instant.now().minusSeconds(1));
        sessaoRepository.saveAndFlush(atual);

        assertErro(refresh(refreshToken), 400, "invalid_grant");
        assertThat(ativas(email)).extracting(Sessao::getJti).containsExactly(jti(refreshToken));
    }

    @Test
    void aRevogacaoRespondeOkEORefreshSeguinteDaInvalidGrant() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();

        HttpResponse<String> resposta = revogar(form(Map.of("token", refreshToken,
                "token_type_hint", "refresh_token", "client_id", "saigt-ui")));

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        assertThat(resposta.body()).isEmpty();
        assertThat(sessaoRepository.findById(jti(refreshToken)).orElseThrow().getRevogadoEm()).isNotNull();
        assertErro(refresh(refreshToken), 400, "invalid_grant");

        assertThat(revogar(form(Map.of("token", refreshToken, "token_type_hint", "refresh_token",
                "client_id", "saigt-ui"))).statusCode()).isEqualTo(200);
        assertThat(revogar(form(Map.of("token", "nao-e-um-token", "client_id", "saigt-ui"))).statusCode()).isEqualTo(200);
    }

    @Test
    void aRevogacaoSemTypeHintTambemTerminaASessao() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();

        assertThat(revogar(form(Map.of("token", refreshToken, "client_id", "saigt-ui"))).statusCode()).isEqualTo(200);

        assertThat(ativas(email)).isEmpty();
    }

    @Test
    void aRevogacaoSemTokenDaInvalidRequestESemClienteDaInvalidClient() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();

        assertErro(revogar(form(Map.of("token_type_hint", "refresh_token", "client_id", "saigt-ui"))), 400,
                "invalid_request");
        assertErro(revogar(form(Map.of("token", refreshToken))), 401, "invalid_client");
        assertErro(revogar("client_id=saigt-ui&token=x&token=y"), 400, "invalid_request");
        assertThat(ativas(email)).extracting(Sessao::getJti).containsExactly(jti(refreshToken));
    }

    @Test
    void depoisDeUmMasterRevogarAsSessoesORefreshDaInvalidGrant() throws Exception {
        String email = emailUnico();
        String refreshToken = login(email).get("refresh_token").asText();
        Utilizador helena = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        Utilizador master = new Utilizador("Master Fictício", "master-" + UUID.randomUUID() + "@dgt.invalid", "AD_DGT");
        master.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.saveAndFlush(master);
        String tokenMaster = emissorTokens.emitirAcesso(new ClaimsUtilizador(master.getId(), master.getNome(),
                master.getEmail(), "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();

        HttpResponse<String> revogacao = http.patchJson("/api/v1/utilizadores/" + helena.getId() + "/sessoes/revogar",
                tokenMaster, "{\"motivo\":\"Teste de revogação\"}");

        assertThat(revogacao.statusCode()).as(revogacao.body()).isEqualTo(200);
        assertErro(refresh(refreshToken), 400, "invalid_grant");
    }

    @Test
    void oRefreshDeUmaSessaoIncmMantemAEntidadeENaoChamaAIncm() throws Exception {
        String email = emailUnico();
        JsonNode inicial = login(email);

        JsonNode rodado = json(refresh(inicial.get("refresh_token").asText()));

        JWTClaimsSet antes = SignedJWT.parse(inicial.get("access_token").asText()).getJWTClaimsSet();
        JWTClaimsSet depois = SignedJWT.parse(rodado.get("access_token").asText()).getJWTClaimsSet();
        assertThat(depois.getClaim("entidade")).isEqualTo(antes.getClaim("entidade"));
        assertThat(depois.getSubject()).isEqualTo(antes.getSubject());
        assertThat(depois.getStringClaim("origem")).isEqualTo("INCM");
        assertThat(depois.getJWTID()).isNotEqualTo(antes.getJWTID());
        verify(incm, times(1)).autenticarComMotivo(any(), any());
    }

    @Test
    void osLogsDoRefreshEDaRevogacaoNaoTemTokens(CapturedOutput output) throws Exception {
        String email = emailUnico();
        JsonNode inicial = login(email);
        JsonNode rodado = json(refresh(inicial.get("refresh_token").asText()));
        refresh(inicial.get("refresh_token").asText());
        revogar(form(Map.of("token", rodado.get("refresh_token").asText(), "client_id", "saigt-ui")));

        assertThat(output.getAll())
                .contains("Refresh concluído")
                .doesNotContain(inicial.get("refresh_token").asText(), rodado.get("refresh_token").asText(),
                        rodado.get("access_token").asText());
    }

    @Test
    void oRefreshTokenNaQueryStringERecusado() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("?refresh_token=x", null, null,
                form(Map.of("grant_type", "refresh_token", "client_id", "saigt-ui")));

        assertErro(resposta, 400, "invalid_request");
    }

    private String refreshJwtComJti(UUID utilizadorId, String jti) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(utilizadorId.toString()).jwtID(jti)
                .issuer("https://auth.saigt.pt").audience(TokenService.AUDIENCIA_REFRESH_TOKEN)
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyProvider.chaveAtual().getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(keyProvider.chaveAtual().toRSAPrivateKey()));
        return jwt.serialize();
    }
}
