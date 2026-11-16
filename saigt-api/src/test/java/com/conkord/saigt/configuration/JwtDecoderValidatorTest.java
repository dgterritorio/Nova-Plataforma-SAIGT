package com.conkord.saigt.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Rotação de chaves, que o JwtIntegrationTest não cobre: withJwkSetUri exige rede real, daí o JWKSet em memória. */
class JwtDecoderValidatorTest {

    private static final String ISSUER = "https://auth.saigt.pt";
    private static final String AUDIENCE = "saigt-api";

    private RSAKey chaveAntiga;
    private RSAKey chaveNova;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        chaveAntiga = new RSAKeyGenerator(2048).keyID("antiga").generate();
        chaveNova = new RSAKeyGenerator(2048).keyID("nova").generate();

        JWKSet jwkSet = new JWKSet(List.<JWK>of(chaveAntiga, chaveNova)).toPublicJWKSet();

        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwkSet)));

        NimbusJwtDecoder nimbusDecoder = new NimbusJwtDecoder(jwtProcessor);

        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(AUDIENCE));
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(new JwtTimestampValidator(), new JwtIssuerValidator(ISSUER), withAudience)));

        decoder = nimbusDecoder;
    }

    @Test
    void deveValidarTokensAssinadosPelaChaveAntigaEPelaNovaDuranteRotacao() {
        String tokenAntigo = assinar(chaveAntiga, Instant.now().plusSeconds(900), ISSUER, AUDIENCE);
        String tokenNovo = assinar(chaveNova, Instant.now().plusSeconds(900), ISSUER, AUDIENCE);

        assertThat(decoder.decode(tokenAntigo)).isNotNull();
        assertThat(decoder.decode(tokenNovo)).isNotNull();
    }

    private String assinar(RSAKey chave, Instant exp, String iss, String aud) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(iss)
                    .audience(aud)
                    .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                    .expirationTime(Date.from(exp))
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID()).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(chave.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
