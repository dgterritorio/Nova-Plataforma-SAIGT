package com.conkord.saigt.service.sessao;

import org.springframework.security.oauth2.jwt.Jwt;
import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.configuration.GrantsUtilizador;
import com.conkord.saigt.configuration.RepositorioClientesSaigt;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.conkord.saigt.service.core.JwtKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** As autorizações do servidor vivem em `sessao`: o que uma instância grava, outra lê. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@ActiveProfiles("test")
class AutorizacoesSessaoIntegrationTest {

    private static final String CONTEXTO = "{\"entidades\":[]}";

    @Autowired
    private OAuth2AuthorizationService autorizacoes;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private RegisteredClientRepository clientes;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EmissorTokensUtilizador emissorTokens;

    @Autowired
    private JwtKeyProvider keyProvider;

    private Utilizador utilizador() {
        return utilizadorRepository.save(new Utilizador("Ana Externa", "ana-" + UUID.randomUUID() + "@exemplo.pt", "INCM"));
    }

    private OAuth2Authorization preAutenticacao(Utilizador utilizador, Jwt token) {
        RegisteredClient ui = clientes.findById(RepositorioClientesSaigt.ID_CLIENTE_UI);
        return OAuth2Authorization.withRegisteredClient(ui)
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(GrantsUtilizador.INCM_PASSWORD)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.PRE_AUTENTICACAO)
                .attribute(AutorizacoesSessao.CONTEXTO_SESSAO, CONTEXTO)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token.getTokenValue(), Instant.now(), token.getExpiresAt()))
                .build();
    }

    private String refreshJwt(UUID utilizadorId, String audiencia, Instant expiraEm) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(utilizadorId.toString()).jwtID(UUID.randomUUID().toString())
                .issuer("https://auth.saigt.pt").audience(audiencia).expirationTime(Date.from(expiraEm)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyProvider.chaveAtual().getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(keyProvider.chaveAtual().toRSAPrivateKey()));
        return jwt.serialize();
    }

    private OAuth2Authorization ativa(Utilizador utilizador, String refreshJwt, Instant expiraEm) {
        RegisteredClient ui = clientes.findById(RepositorioClientesSaigt.ID_CLIENTE_UI);
        return OAuth2Authorization.withRegisteredClient(ui)
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(GrantsUtilizador.ENTIDADE)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.ATIVA)
                .refreshToken(new OAuth2RefreshToken(refreshJwt, Instant.now(), expiraEm))
                .build();
    }

    @Test
    void deveGravarAPreAutenticacaoEmSessaoELaComOutraInstancia() {
        Utilizador ana = utilizador();
        Jwt token = emissorTokens.emitirPreAutenticacao(ana.getId());

        autorizacoes.save(preAutenticacao(ana, token));

        Sessao linha = sessaoRepository.findById(token.getId()).orElseThrow();
        assertThat(linha.getTipo()).isEqualTo("PRE_AUTENTICACAO");
        assertThat(linha.getContextoSessao()).contains("entidades");
        assertThat(linha.getRevogadoEm()).isNull();

        AutorizacoesSessao outraInstancia = new AutorizacoesSessao(sessaoRepository, utilizadorRepository, clientes, tokenService);
        OAuth2Authorization lida = outraInstancia.findByToken(token.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN);
        assertThat(lida).isNotNull();
        assertThat(lida.getId()).isEqualTo(token.getId());
        assertThat(lida.getPrincipalName()).isEqualTo(ana.getId().toString());
        assertThat(lida.getRegisteredClientId()).isEqualTo(RepositorioClientesSaigt.ID_CLIENTE_UI);
        assertThat(lida.<String>getAttribute(AutorizacoesSessao.CONTEXTO_SESSAO)).isEqualTo(CONTEXTO);
        assertThat(lida.getAccessToken().isActive()).isTrue();
    }

    @Test
    void deveGravarASessaoAtivaSoComOJtiDoRefreshTokenENuncaOToken() throws Exception {
        Utilizador ana = utilizador();
        Instant expiraEm = Instant.now().plusSeconds(8 * 3600);
        String refresh = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        String jti = SignedJWT.parse(refresh).getJWTClaimsSet().getJWTID();

        autorizacoes.save(ativa(ana, refresh, expiraEm));

        Sessao linha = sessaoRepository.findById(jti).orElseThrow();
        assertThat(linha.getTipo()).isEqualTo("ATIVA");
        assertThat(linha.getJti()).isEqualTo(jti).isNotEqualTo(refresh);
        assertThat(linha.getExpiraEm()).isCloseTo(expiraEm, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(sessaoRepository.findAll()).noneMatch(s -> refresh.equals(s.getJti()));

        OAuth2Authorization lida = autorizacoes.findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(lida).isNotNull();
        assertThat(lida.getRefreshToken().isActive()).isTrue();
        assertThat(lida.getAuthorizationGrantType()).isEqualTo(GrantsUtilizador.ENTIDADE);
    }

    @Test
    void deveMarcarARevogacaoQuandoOTokenFicaInvalidado() throws Exception {
        Utilizador ana = utilizador();
        Instant expiraEm = Instant.now().plusSeconds(3600);
        String refresh = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        OAuth2Authorization autorizacao = ativa(ana, refresh, expiraEm);
        autorizacoes.save(autorizacao);

        OAuth2Authorization invalidada = OAuth2Authorization.from(autorizacao)
                .token(autorizacao.getRefreshToken().getToken(),
                        metadados -> metadados.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true))
                .build();
        autorizacoes.save(invalidada);

        String jti = SignedJWT.parse(refresh).getJWTClaimsSet().getJWTID();
        assertThat(sessaoRepository.findById(jti).orElseThrow().getRevogadoEm()).isNotNull();
        OAuth2Authorization lida = autorizacoes.findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(lida.getRefreshToken().isInvalidated()).isTrue();
        assertThat(lida.getRefreshToken().isActive()).isFalse();
    }

    @Test
    void removerRevogaALinhaSemAApagar() throws Exception {
        Utilizador ana = utilizador();
        Instant expiraEm = Instant.now().plusSeconds(3600);
        String refresh = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        OAuth2Authorization autorizacao = ativa(ana, refresh, expiraEm);
        autorizacoes.save(autorizacao);

        autorizacoes.remove(autorizacao);

        String jti = SignedJWT.parse(refresh).getJWTClaimsSet().getJWTID();
        assertThat(sessaoRepository.findById(jti).orElseThrow().getRevogadoEm()).isNotNull();
    }

    @Test
    void naoDeveEncontrarTokensDeOutraAudienciaExpiradosAdulteradosOuDeOutroTipo() throws Exception {
        Utilizador ana = utilizador();
        Jwt pre = emissorTokens.emitirPreAutenticacao(ana.getId());
        autorizacoes.save(preAutenticacao(ana, pre));
        Instant expiraEm = Instant.now().plusSeconds(3600);
        String refresh = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        autorizacoes.save(ativa(ana, refresh, expiraEm));

        assertThat(autorizacoes.findByToken(pre.getTokenValue(), OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(autorizacoes.findByToken(refresh, OAuth2TokenType.ACCESS_TOKEN)).isNull();
        String acesso = emissorTokens.emitirAcesso(new com.conkord.saigt.service.core.ClaimsUtilizador(ana.getId(), "Ana",
                "ana@exemplo.pt", "AD_DGT", com.conkord.saigt.persistence.operacional.PerfilInterno.MASTER, null, null)).getTokenValue();
        assertThat(autorizacoes.findByToken(acesso, OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(autorizacoes.findByToken(acesso, OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(autorizacoes.findByToken(refresh + "x", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(autorizacoes.findByToken("nao-e-jwt", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(autorizacoes.findByToken(acesso, null)).isNull();
        String expirado = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, Instant.now().minusSeconds(5));
        assertThat(autorizacoes.findByToken(expirado, OAuth2TokenType.REFRESH_TOKEN)).isNull();
        String semLinha = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        assertThat(autorizacoes.findByToken(semLinha, OAuth2TokenType.REFRESH_TOKEN)).isNull();
        String deOutroUtilizador = refreshJwtComJti(UUID.randomUUID(), SignedJWT.parse(refresh).getJWTClaimsSet().getJWTID(), expiraEm);
        assertThat(autorizacoes.findByToken(deOutroUtilizador, OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    @Test
    void semTipoDeTokenDeveProcurarNoRefreshENaPreAutenticacao() throws Exception {
        Utilizador ana = utilizador();
        Jwt pre = emissorTokens.emitirPreAutenticacao(ana.getId());
        autorizacoes.save(preAutenticacao(ana, pre));
        Instant expiraEm = Instant.now().plusSeconds(3600);
        String refresh = refreshJwt(ana.getId(), TokenService.AUDIENCIA_REFRESH_TOKEN, expiraEm);
        autorizacoes.save(ativa(ana, refresh, expiraEm));

        assertThat(autorizacoes.findByToken(refresh, null).getId()).isEqualTo(SignedJWT.parse(refresh).getJWTClaimsSet().getJWTID());
        assertThat(autorizacoes.findByToken(pre.getTokenValue(), null).getId()).isEqualTo(pre.getId());
    }

    private String refreshJwtComJti(UUID utilizadorId, String jti, Instant expiraEm) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(utilizadorId.toString()).jwtID(jti)
                .issuer("https://auth.saigt.pt").audience(TokenService.AUDIENCIA_REFRESH_TOKEN)
                .expirationTime(Date.from(expiraEm)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyProvider.chaveAtual().getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(keyProvider.chaveAtual().toRSAPrivateKey()));
        return jwt.serialize();
    }

    @Test
    void naoDeveGravarAutorizacoesDeClientesDeSistema() {
        RegisteredClient sistema = RegisteredClient.withId("snit").clientId("snit-teste")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret("{noop}x").build();
        long antes = sessaoRepository.count();

        autorizacoes.save(OAuth2Authorization.withRegisteredClient(sistema).principalName("snit-teste")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build());

        assertThat(sessaoRepository.count()).isEqualTo(antes);
    }

    @Test
    void deveRecusarAutorizacoesDoUiIncompletas() {
        Utilizador ana = utilizador();
        RegisteredClient ui = clientes.findById(RepositorioClientesSaigt.ID_CLIENTE_UI);

        OAuth2Authorization semTipo = OAuth2Authorization.withRegisteredClient(ui).principalName(ana.getId().toString())
                .authorizationGrantType(GrantsUtilizador.ENTIDADE).build();
        OAuth2Authorization semContexto = OAuth2Authorization.withRegisteredClient(ui).principalName(ana.getId().toString())
                .authorizationGrantType(GrantsUtilizador.INCM_PASSWORD)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.PRE_AUTENTICACAO)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        emissorTokens.emitirPreAutenticacao(ana.getId()).getTokenValue(), Instant.now(), Instant.now().plusSeconds(60)))
                .build();

        assertThatThrownBy(() -> autorizacoes.save(semTipo)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> autorizacoes.save(semContexto)).isInstanceOf(IllegalArgumentException.class);
    }
}
