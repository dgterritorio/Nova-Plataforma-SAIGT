package com.conkord.saigt.service.core;

import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.exception.SessaoInvalidaException;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private TokenService tokenService;
    private EmissorTokensUtilizador emissor;

    @BeforeEach
    void setUp() {
        JwtKeyProvider keyProvider = novaChave();
        tokenService = new TokenService(keyProvider, "https://auth.saigt.pt");
        emissor = emissorCom(keyProvider);
    }

    private static JwtKeyProvider novaChave() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        JwtKeyProvider keyProvider = new JwtKeyProvider("", env);
        keyProvider.carregar();
        return keyProvider;
    }

    private static EmissorTokensUtilizador emissorCom(JwtKeyProvider keyProvider) {
        return new EmissorTokensUtilizador(new NimbusJwtEncoder(
                (selector, context) -> selector.select(new JWKSet(keyProvider.chaveAtual()))),
                "https://auth.saigt.pt", "saigt-api", 15, 5);
    }

    @Test
    void deveValidarJwtProprioSoComAAudienciaEsperada() {
        Jwt token = emissor.emitirPreAutenticacao(UUID.randomUUID());

        JWTClaimsSet claims = tokenService.validarJwtProprio(token.getTokenValue(), TokenService.AUDIENCIA_PRE_AUTENTICACAO);

        assertThat(claims.getJWTID()).isEqualTo(token.getId());
        assertThatThrownBy(() -> tokenService.validarJwtProprio(token.getTokenValue(), TokenService.AUDIENCIA_REFRESH_TOKEN))
                .isInstanceOf(SessaoInvalidaException.class);
        String accessToken = emissor.emitirAcesso(new ClaimsUtilizador(UUID.randomUUID(), "Ana Silva", "ana@dgt.pt",
                "AD_DGT", PerfilInterno.MASTER, null, null)).getTokenValue();
        assertThatThrownBy(() -> tokenService.validarJwtProprio(accessToken, TokenService.AUDIENCIA_REFRESH_TOKEN))
                .isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> tokenService.validarJwtProprio(accessToken, TokenService.AUDIENCIA_PRE_AUTENTICACAO))
                .isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> tokenService.validarJwtProprio("isto-nao-e-um-jwt", TokenService.AUDIENCIA_REFRESH_TOKEN))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void deveRecusarUmJwtAssinadoComOutraChave() {
        String alheio = emissorCom(novaChave()).emitirPreAutenticacao(UUID.randomUUID()).getTokenValue();

        assertThatThrownBy(() -> tokenService.validarJwtProprio(alheio, TokenService.AUDIENCIA_PRE_AUTENTICACAO))
                .isInstanceOf(SessaoInvalidaException.class);
    }
}
