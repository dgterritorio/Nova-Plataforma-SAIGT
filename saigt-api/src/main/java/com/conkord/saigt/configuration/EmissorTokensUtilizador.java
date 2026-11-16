package com.conkord.saigt.configuration;

import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.core.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens dos grants de utilizador (`incm-password`, `entidade`, `ad-password`, `refresh_token`), assinados pelo
 * mesmo {@link JwtEncoder} do servidor de autorização que emite os tokens dos sistemas e os refresh tokens. O
 * `iss`/`aud` são os que o resource server ({@link SecurityConfig}) exige.
 */
@Component
public class EmissorTokensUtilizador {

    private static final int BYTES_JTI_PRE_AUTENTICACAO = 32;
    private static final String CLAIM_EMAIL = "email";

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final Duration validadeAcesso;
    private final Duration validadePreAutenticacao;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmissorTokensUtilizador(JwtEncoder jwtEncoder,
                                   @Value("${app.jwt.issuer}") String issuer,
                                   @Value("${app.jwt.audience}") String audience,
                                   @Value("${app.jwt.access-token-ttl-minutes}") long validadeAcessoMinutos,
                                   @Value("${app.jwt.pre-auth-token-ttl-minutes}") long validadePreAutenticacaoMinutos) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.validadeAcesso = Duration.ofMinutes(validadeAcessoMinutos);
        this.validadePreAutenticacao = Duration.ofMinutes(validadePreAutenticacaoMinutos);
    }

    public Jwt emitirAcesso(ClaimsUtilizador claims) {
        if (claims.perfilInterno() != null && claims.entidade() != null) {
            throw new IllegalStateException(
                    "Claims inconsistentes: um utilizador não pode ter perfil_interno e entidade em simultâneo.");
        }
        if (claims.perfilInterno() != null && claims.cargo() != null) {
            throw new IllegalStateException(
                    "Claims inconsistentes: um utilizador interno (perfil_interno) não pode ter cargo — só a INCM o devolve.");
        }
        if (claims.entidade() != null && claims.entidade().id() == null) {
            throw new IllegalStateException("Claims inconsistentes: a entidade do token tem de ser uma entidade SAIGT.");
        }
        Instant agora = agora();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .subject(claims.utilizadorId().toString())
                .issuer(issuer)
                .audience(List.of(audience))
                .id(UUID.randomUUID().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(validadeAcesso));
        if (claims.nome() != null) {
            builder.claim("name", claims.nome());
        }
        if (claims.email() != null) {
            builder.claim(CLAIM_EMAIL, claims.email());
        }
        if (claims.origem() != null) {
            builder.claim("origem", claims.origem());
        }
        if (claims.perfilInterno() != null) {
            builder.claim("perfil_interno", claims.perfilInterno().name());
        }
        if (claims.cargo() != null) {
            builder.claim("cargo", claims.cargo());
        }
        if (claims.entidade() != null) {
            builder.claim("entidade", construirEntidade(claims.entidade()));
        }
        return assinar(builder.build());
    }

    /**
     * Só identidade, sem privilégios: o `aud` restrito basta para os restantes endpoints o rejeitarem. O `jti` é
     * opaco e identifica a linha `sessao` PRE_AUTENTICACAO que retém as adesões até à escolha de entidade.
     */
    public Jwt emitirPreAutenticacao(UUID utilizadorId) {
        Instant agora = agora();
        return assinar(JwtClaimsSet.builder()
                .subject(utilizadorId.toString())
                .id(gerarValorOpaco())
                .issuer(issuer)
                .audience(List.of(TokenService.AUDIENCIA_PRE_AUTENTICACAO))
                .issuedAt(agora)
                .expiresAt(agora.plus(validadePreAutenticacao))
                .build());
    }

    /** Ao segundo, como os `iat`/`exp` do token: o `expires_in` e a validade gravada coincidem com o token. */
    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private Jwt assinar(JwtClaimsSet claims) {
        JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(cabecalho, claims));
    }

    /** Campos ausentes na origem ficam ausentes do token, nunca `null`. */
    private static Map<String, Object> construirEntidade(ClaimsUtilizador.ClaimsEntidade entidade) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", entidade.id());
        if (entidade.codigo() != null) {
            mapa.put("codigo", entidade.codigo());
        }
        if (entidade.tipoEntidade() != null) {
            mapa.put("tipo_entidade", entidade.tipoEntidade());
        }
        if (entidade.dico() != null) {
            mapa.put("dico", entidade.dico());
        }
        mapa.put("nipc", entidade.nipc());
        mapa.put("nome", entidade.nome());
        if (entidade.incmEnrollmentId() != null) {
            mapa.put("incm_enrollment_id", entidade.incmEnrollmentId());
        }
        mapa.put("pcm", entidade.pcm());
        if (entidade.morada() != null) {
            ClaimsUtilizador.Morada morada = entidade.morada();
            Map<String, Object> moradaMapa = new LinkedHashMap<>();
            moradaMapa.put("arteria", morada.arteria());
            moradaMapa.put("numero", morada.numero());
            moradaMapa.put("piso", morada.piso());
            moradaMapa.put("codigo_postal", morada.codigoPostal());
            moradaMapa.put("designacao_postal", morada.designacaoPostal());
            moradaMapa.put("localidade", morada.localidade());
            mapa.put("morada", moradaMapa);
        }
        if (entidade.telefone() != null) {
            mapa.put("telefone", entidade.telefone());
        }
        if (entidade.email() != null) {
            mapa.put(CLAIM_EMAIL, entidade.email());
        }
        if (entidade.drParte() != null) {
            ClaimsUtilizador.DrParte drParte = entidade.drParte();
            Map<String, Object> drParteMapa = new LinkedHashMap<>();
            drParteMapa.put("id", drParte.id());
            drParteMapa.put("letra", drParte.letra());
            if (drParte.descricao() != null) {
                drParteMapa.put("descricao", drParte.descricao());
            }
            mapa.put("dr_parte", drParteMapa);
        }
        if (entidade.atosAutorizados() != null) {
            mapa.put("atos_autorizados", construirAtosAutorizados(entidade.atosAutorizados()));
        }
        return mapa;
    }

    private static Map<String, Object> construirAtosAutorizados(ClaimsUtilizador.AtosAutorizados atos) {
        List<Map<String, Object>> serie2 = atos.serie2().stream()
                .map(EmissorTokensUtilizador::construirAtoAutorizado)
                .toList();
        return Map.of("serie_1", atos.serie1(), "serie_2", serie2);
    }

    private static Map<String, Object> construirAtoAutorizado(ClaimsUtilizador.AtoAutorizado ato) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", ato.id());
        if (ato.nome() != null) {
            mapa.put("nome", ato.nome());
        }
        mapa.put("extrato", ato.extrato());
        return mapa;
    }

    private String gerarValorOpaco() {
        byte[] bytes = new byte[BYTES_JTI_PRE_AUTENTICACAO];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
