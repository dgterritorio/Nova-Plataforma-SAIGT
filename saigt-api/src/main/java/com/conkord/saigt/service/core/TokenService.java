package com.conkord.saigt.service.core;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.conkord.saigt.exception.SessaoInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Set;

/**
 * Só valida os JWT de sessão que o servidor de autorização emitiu (pré-autenticação e refresh token). Não emite
 * tokens: os de utilizador saem do {@code EmissorTokensUtilizador}, os de sistema e os refresh tokens do gerador do
 * servidor de autorização; o access token é validado pelo resource server (SecurityConfig) contra o JWKS.
 */
@Service
public class TokenService {

    /** Aceite só no grant de escolha de entidade. */
    public static final String AUDIENCIA_PRE_AUTENTICACAO = "saigt-api-pre-auth";
    /** Aceite só como refresh token, nunca como token de acesso. */
    public static final String AUDIENCIA_REFRESH_TOKEN = "saigt-api-refresh";

    private final JwtKeyProvider keyProvider;
    private final String issuer;

    public TokenService(JwtKeyProvider keyProvider, @Value("${app.jwt.issuer}") String issuer) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
    }

    /**
     * Valida um JWT que o próprio servidor emitiu (assinatura, `iss`, `aud` exato, `exp`, sem tolerância de relógio) e
     * devolve as claims; `jti` e `sub` são obrigatórios. Qualquer falha é {@link SessaoInvalidaException}.
     */
    public JWTClaimsSet validarJwtProprio(String token, String audienciaEsperada) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWK chavePublica = keyProvider.jwkSet().getKeyByKeyId(jwt.getHeader().getKeyID());
            if (!(chavePublica instanceof RSAKey rsaKey) || !jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
                throw new SessaoInvalidaException("Token inválido.");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            verificador(audienciaEsperada).verify(claims, null);
            if (claims.getJWTID() == null || claims.getSubject() == null) {
                throw new SessaoInvalidaException("Token inválido.");
            }
            return claims;
        } catch (BadJOSEException | ParseException | JOSEException e) {
            throw new SessaoInvalidaException("Token inválido ou expirado.");
        }
    }

    /** `aud` restrito, `iss` exato e `exp` obrigatório, sem tolerância de relógio. */
    private DefaultJWTClaimsVerifier<?> verificador(String audiencia) {
        DefaultJWTClaimsVerifier<?> verificador = new DefaultJWTClaimsVerifier<>(audiencia,
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of(JWTClaimNames.EXPIRATION_TIME));
        verificador.setMaxClockSkew(0);
        return verificador;
    }
}
