package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.core.JwtKeyProvider;
import com.conkord.saigt.service.core.TokenService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os claims dos tokens de utilizador são os mesmos que o emissor antigo punha. */
class EmissorTokensUtilizadorTest {

    private JwtKeyProvider keyProvider;
    private EmissorTokensUtilizador emissor;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        keyProvider = new JwtKeyProvider("", env);
        // carregar() é do pacote do JwtKeyProvider: no contexto Spring corre pelo @PostConstruct.
        ReflectionTestUtils.invokeMethod(keyProvider, "carregar");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                (selector, context) -> selector.select(new JWKSet(keyProvider.chaveAtual())));
        emissor = new EmissorTokensUtilizador(encoder, "https://auth.saigt.pt", "saigt-api", 15, 5);
        tokenService = new TokenService(keyProvider, "https://auth.saigt.pt");
    }

    private ClaimsUtilizador claimsInterno(UUID utilizadorId) {
        return new ClaimsUtilizador(utilizadorId, "Ana Silva", "ana@dgt.pt", "AD_DGT", PerfilInterno.MASTER, null, null);
    }

    private ClaimsUtilizador claimsIncm(UUID utilizadorId, ClaimsUtilizador.ClaimsEntidade entidade, String cargo) {
        return new ClaimsUtilizador(utilizadorId, "João Costa", "joao@cm-lisboa.pt", "INCM", null, entidade, cargo);
    }

    @Test
    void deveEmitirAccessTokenComPerfilInternoSemEntidade() throws Exception {
        UUID utilizadorId = UUID.randomUUID();

        SignedJWT jwt = SignedJWT.parse(emissor.emitirAcesso(claimsInterno(utilizadorId)).getTokenValue());
        JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();

        assertThat(jwt.getHeader().getKeyID()).isNotBlank();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(claimsSet.getSubject()).isEqualTo(utilizadorId.toString());
        assertThat(claimsSet.getStringClaim("name")).isEqualTo("Ana Silva");
        assertThat(claimsSet.getStringClaim("email")).isEqualTo("ana@dgt.pt");
        assertThat(claimsSet.getStringClaim("origem")).isEqualTo("AD_DGT");
        assertThat(claimsSet.getStringClaim("perfil_interno")).isEqualTo("MASTER");
        assertThat(claimsSet.getIssuer()).isEqualTo("https://auth.saigt.pt");
        assertThat(claimsSet.getAudience()).containsExactly("saigt-api");
        assertThat(Duration.between(Instant.now(), claimsSet.getExpirationTime().toInstant()))
                .isCloseTo(Duration.ofMinutes(15), Duration.ofSeconds(5));

        assertThat(claimsSet.getClaim("entidade")).isNull();
        assertThat(claimsSet.getClaim("entidade_id")).isNull();
        assertThat(claimsSet.getClaim("atos_autorizados")).isNull();
    }

    @Test
    void deveEmitirAccessTokenComEntidadeEspelhadaSemPerfilInterno() throws Exception {
        UUID utilizadorId = UUID.randomUUID();
        ClaimsUtilizador.ClaimsEntidade entidade = new ClaimsUtilizador.ClaimsEntidade(
                7L, "5245", "CM", "1111", "512523698", "Município de Lisboa", "2015482", false, null, null, null,
                new ClaimsUtilizador.DrParte(1234567891234567L, "C", "Administração Pública"),
                new ClaimsUtilizador.AtosAutorizados(List.of(),
                        List.of(new ClaimsUtilizador.AtoAutorizado(1234567891234568L, "Despacho", true),
                                new ClaimsUtilizador.AtoAutorizado(1234567891234570L, null, false))));
        ClaimsUtilizador claims = claimsIncm(utilizadorId, entidade, null);

        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet();

        assertThat(claimsSet.getStringClaim("origem")).isEqualTo("INCM");
        assertThat(claimsSet.getClaim("perfil_interno")).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> entidadeClaim = (Map<String, Object>) claimsSet.getClaim("entidade");
        assertThat(entidadeClaim)
                .containsEntry("id", 7L)
                .containsEntry("codigo", "5245")
                .containsEntry("tipo_entidade", "CM")
                .containsEntry("dico", "1111")
                .containsEntry("nipc", "512523698")
                .containsEntry("nome", "Município de Lisboa")
                .containsEntry("incm_enrollment_id", "2015482")
                .containsEntry("pcm", false)
                .doesNotContainKeys("nif", "responsavel", "morada", "telefone", "email");
        assertThat(claimsSet.getJWTID()).isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Object> drParte = (Map<String, Object>) entidadeClaim.get("dr_parte");
        assertThat(drParte)
                .containsEntry("letra", "C")
                .containsEntry("descricao", "Administração Pública");

        @SuppressWarnings("unchecked")
        Map<String, Object> atos = (Map<String, Object>) entidadeClaim.get("atos_autorizados");
        assertThat((List<?>) atos.get("serie_1")).isEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> serie2 = (List<Map<String, Object>>) atos.get("serie_2");
        assertThat(serie2).hasSize(2);
        assertThat(serie2.get(0)).containsEntry("id", 1234567891234568L).containsEntry("nome", "Despacho").containsEntry("extrato", true);
        assertThat(serie2.get(1)).containsEntry("id", 1234567891234570L).containsEntry("extrato", false).doesNotContainKey("nome");
    }

    @Test
    void naoDevePublicarDicoQuandoAEntidadeSaigtNaoTem() throws Exception {
        ClaimsUtilizador.ClaimsEntidade cim = new ClaimsUtilizador.ClaimsEntidade(
                8L, "3001", "CIM", null, "509999999", "CIM Teste", "2015499", false, null, null, null,
                null, new ClaimsUtilizador.AtosAutorizados(List.of(), List.of()));

        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claimsIncm(UUID.randomUUID(), cim, null)).getTokenValue())
                .getJWTClaimsSet();

        @SuppressWarnings("unchecked")
        Map<String, Object> entidadeClaim = (Map<String, Object>) claimsSet.getClaim("entidade");
        assertThat(entidadeClaim).containsEntry("tipo_entidade", "CIM").doesNotContainKey("dico");
    }

    @Test
    void deveEmitirJtiDiferenteEmCadaAccessToken() throws Exception {
        ClaimsUtilizador claims = claimsInterno(UUID.randomUUID());

        String primeiro = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet().getJWTID();
        String segundo = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet().getJWTID();

        assertThat(primeiro).isNotBlank().isNotEqualTo(segundo);
    }

    @Test
    void naoDeveEmitirAccessTokenQuandoAEntidadeNaoTemIdSaigt() {
        ClaimsUtilizador.ClaimsEntidade semId = new ClaimsUtilizador.ClaimsEntidade(
                null, null, null, null, "512523698", "Município de Lisboa", "2015482", false, null, null, null,
                null, null);

        assertThatThrownBy(() -> emissor.emitirAcesso(claimsIncm(UUID.randomUUID(), semId, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devePublicarDrParteAusenteQuandoEntidadeNaoTemParteDeSegundaSerie() throws Exception {
        UUID utilizadorId = UUID.randomUUID();
        ClaimsUtilizador.ClaimsEntidade entidade = new ClaimsUtilizador.ClaimsEntidade(
                7L, "5245", "CM", "1111", "512523698", "Município de Lisboa", "2015482", false, null, null, null,
                null, new ClaimsUtilizador.AtosAutorizados(List.of(), List.of()));
        ClaimsUtilizador claims = claimsIncm(utilizadorId, entidade, null);

        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet();

        @SuppressWarnings("unchecked")
        Map<String, Object> entidadeClaim = (Map<String, Object>) claimsSet.getClaim("entidade");
        assertThat(entidadeClaim).doesNotContainKey("dr_parte");
    }

    @Test
    void deveIncluirClaimCargoQuandoPresenteNumUtilizadorIncm() throws Exception {
        UUID utilizadorId = UUID.randomUUID();
        ClaimsUtilizador.ClaimsEntidade entidade = new ClaimsUtilizador.ClaimsEntidade(
                7L, "5245", "CM", "1111", "512523698", "Município de Lisboa", null, false, null, null, null, null,
                new ClaimsUtilizador.AtosAutorizados(List.of(), List.of()));
        ClaimsUtilizador claims = claimsIncm(utilizadorId, entidade, "Técnica Superior de Urbanismo");

        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet();

        assertThat(claimsSet.getStringClaim("cargo")).isEqualTo("Técnica Superior de Urbanismo");
    }

    @Test
    void naoDeveIncluirClaimCargoQuandoAusenteNumUtilizadorIncm() throws Exception {
        UUID utilizadorId = UUID.randomUUID();
        ClaimsUtilizador.ClaimsEntidade entidade = new ClaimsUtilizador.ClaimsEntidade(
                7L, "5245", "CM", "1111", "512523698", "Município de Lisboa", null, false, null, null, null, null,
                new ClaimsUtilizador.AtosAutorizados(List.of(), List.of()));
        ClaimsUtilizador claims = claimsIncm(utilizadorId, entidade, null);

        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claims).getTokenValue()).getJWTClaimsSet();

        assertThat(claimsSet.getClaim("cargo")).isNull();
    }

    @Test
    void naoDeveIncluirClaimCargoParaUtilizadorInterno() throws Exception {
        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claimsInterno(UUID.randomUUID())).getTokenValue())
                .getJWTClaimsSet();

        assertThat(claimsSet.getClaim("cargo")).isNull();
    }

    @Test
    void deveRejeitarClaimsComPerfilInternoECargoEmSimultaneo() {
        ClaimsUtilizador claims = new ClaimsUtilizador(UUID.randomUUID(), "Ana Silva", "ana@dgt.pt", "AD_DGT",
                PerfilInterno.MASTER, null, "Cargo indevido");

        assertThatThrownBy(() -> emissor.emitirAcesso(claims))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveRejeitarClaimsComPerfilInternoEEntidadeEmSimultaneo() {
        ClaimsUtilizador.ClaimsEntidade entidade = new ClaimsUtilizador.ClaimsEntidade(
                7L, "5245", "CM", "1111", "512523698", "Município de Lisboa", null, false, null, null, null, null, null);
        ClaimsUtilizador claims = new ClaimsUtilizador(UUID.randomUUID(), "Ana Silva", "ana@dgt.pt", "AD_DGT",
                PerfilInterno.MASTER, entidade, null);

        assertThatThrownBy(() -> emissor.emitirAcesso(claims))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void naoDeveIncluirActQuandoEmitidoPeloFluxoNormal() throws Exception {
        JWTClaimsSet claimsSet = SignedJWT.parse(emissor.emitirAcesso(claimsInterno(UUID.randomUUID())).getTokenValue())
                .getJWTClaimsSet();

        assertThat(claimsSet.getClaim("act")).isNull();
    }

    @Test
    void deveAssinarComAChaveAtualETipoJwtEDevolverAValidadeDoToken() throws Exception {
        Jwt jwt = emissor.emitirAcesso(claimsInterno(UUID.randomUUID()));

        SignedJWT assinado = SignedJWT.parse(jwt.getTokenValue());
        assertThat(assinado.getHeader().getKeyID()).isEqualTo(keyProvider.chaveAtual().getKeyID());
        assertThat(assinado.getHeader().getType().getType()).isEqualTo("JWT");
        assertThat(assinado.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(jwt.getIssuedAt());
        assertThat(assinado.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(jwt.getExpiresAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void deveEmitirTokenPreAutenticacaoComAudRestritoETtlDeCincoMinutosEValidarComSucesso() throws Exception {
        UUID utilizadorId = UUID.randomUUID();

        Jwt preAuth = emissor.emitirPreAutenticacao(utilizadorId);

        JWTClaimsSet claimsSet = SignedJWT.parse(preAuth.getTokenValue()).getJWTClaimsSet();
        assertThat(claimsSet.getAudience()).containsExactly("saigt-api-pre-auth");
        assertThat(claimsSet.getJWTID()).isEqualTo(preAuth.getId());
        assertThat(claimsSet.getClaims()).doesNotContainKeys("name", "email", "origem", "perfil_interno", "entidade");
        assertThat(Duration.between(Instant.now(), claimsSet.getExpirationTime().toInstant()))
                .isCloseTo(Duration.ofMinutes(5), Duration.ofSeconds(5));

        JWTClaimsSet validado = tokenService.validarJwtProprio(preAuth.getTokenValue(),
                TokenService.AUDIENCIA_PRE_AUTENTICACAO);
        assertThat(validado.getSubject()).isEqualTo(utilizadorId.toString());
        assertThat(validado.getJWTID()).isEqualTo(preAuth.getId());
    }
}
