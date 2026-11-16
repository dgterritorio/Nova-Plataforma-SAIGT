package com.conkord.saigt.service.sessao;

import com.conkord.saigt.configuration.GrantsUtilizador;
import com.conkord.saigt.configuration.RepositorioClientesSaigt;
import com.conkord.saigt.exception.SessaoInvalidaException;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.TokenService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Autorizações do servidor OAuth sobre `sessao` (DB-IAM-004), partilhadas entre instâncias. Uma autorização do
 * `saigt-ui` é uma linha: `PRE_AUTENTICACAO` (a chave é o `jti` do token de pré-autenticação) ou `ATIVA` (o `jti` do
 * refresh token). A base de dados nunca guarda um token, só o `jti`; por isso a procura por token valida primeiro a
 * assinatura, o emissor, a audiência e a validade, e só depois lê a linha. Os tokens de sistema não criam linhas.
 * O contexto de adesões segue no atributo {@link #CONTEXTO_SESSAO}; o tipo no atributo {@link #TIPO_SESSAO}.
 */
@Component
public class AutorizacoesSessao implements OAuth2AuthorizationService {

    public static final String TIPO_SESSAO = "saigt.tipo_sessao";
    public static final String CONTEXTO_SESSAO = "saigt.contexto_sessao";
    public static final String PRE_AUTENTICACAO = "PRE_AUTENTICACAO";
    public static final String ATIVA = "ATIVA";
    /** No contexto do gerador: o refresh token de uma rotação não passa do `expira_em` da sessão. */
    public static final String FIM_SESSAO = "saigt.fim_sessao";
    private static final String ORIGEM_INCM = "INCM";

    private final SessaoRepository sessaoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final RegisteredClientRepository clientes;
    private final TokenService tokenService;

    public AutorizacoesSessao(SessaoRepository sessaoRepository, UtilizadorRepository utilizadorRepository,
                              RegisteredClientRepository clientes, TokenService tokenService) {
        this.sessaoRepository = sessaoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.clientes = clientes;
        this.tokenService = tokenService;
    }

    /** Insere ou atualiza a linha do `jti`; um token invalidado preenche `revogado_em`. Sistemas: não grava. */
    @Override
    @Transactional
    public void save(OAuth2Authorization autorizacao) {
        if (!RepositorioClientesSaigt.ID_CLIENTE_UI.equals(autorizacao.getRegisteredClientId())) {
            return;
        }
        String tipo = autorizacao.getAttribute(TIPO_SESSAO);
        String jti;
        Instant expiraEm;
        boolean invalidada;
        if (PRE_AUTENTICACAO.equals(tipo)) {
            OAuth2Authorization.Token<OAuth2AccessToken> token = autorizacao.getToken(OAuth2AccessToken.class);
            exigir(token != null, "A pré-autenticação não tem token de acesso.");
            jti = jtiDe(token.getToken().getTokenValue());
            expiraEm = token.getToken().getExpiresAt();
            invalidada = token.isInvalidated();
        } else if (ATIVA.equals(tipo)) {
            OAuth2Authorization.Token<OAuth2RefreshToken> token = autorizacao.getRefreshToken();
            exigir(token != null, "A sessão ativa não tem refresh token.");
            jti = jtiDe(token.getToken().getTokenValue());
            expiraEm = token.getToken().getExpiresAt();
            invalidada = token.isInvalidated();
        } else {
            throw new IllegalArgumentException("Tipo de sessão em falta ou desconhecido.");
        }
        String contexto = autorizacao.getAttribute(CONTEXTO_SESSAO);
        Utilizador utilizador = utilizadorRepository.findById(UUID.fromString(autorizacao.getPrincipalName()))
                .orElseThrow(() -> new IllegalArgumentException("Utilizador da autorização não existe."));

        Sessao sessao = sessaoRepository.findById(jti).orElse(null);
        if (sessao == null) {
            if (PRE_AUTENTICACAO.equals(tipo)) {
                exigir(contexto != null, "A pré-autenticação precisa do contexto de adesões.");
                sessao = Sessao.paraPreAutenticacao(jti, utilizador, expiraEm, contexto);
            } else {
                sessao = new Sessao(jti, utilizador, expiraEm);
                sessao.setContextoSessao(contexto);
            }
        } else {
            exigir(tipo.equals(sessao.getTipo()), "O tipo da sessão não muda.");
            sessao.setExpiraEm(expiraEm);
        }
        if (invalidada && sessao.getRevogadoEm() == null) {
            sessao.setRevogadoEm(Instant.now());
        }
        sessaoRepository.save(sessao);
    }

    /** Nunca apaga: revoga a linha, que a purga remove depois. */
    @Override
    @Transactional
    public void remove(OAuth2Authorization autorizacao) {
        String jti = autorizacao.getRefreshToken() != null ? jtiDe(autorizacao.getRefreshToken().getToken().getTokenValue())
                : autorizacao.getToken(OAuth2AccessToken.class) != null
                ? jtiDe(autorizacao.getToken(OAuth2AccessToken.class).getToken().getTokenValue()) : autorizacao.getId();
        sessaoRepository.findById(jti).ifPresent(sessao -> {
            if (sessao.getRevogadoEm() == null) {
                sessao.setRevogadoEm(Instant.now());
                sessaoRepository.save(sessao);
            }
        });
    }

    /** O id é o `jti`. Sem os tokens: o valor do token só existe em quem o apresenta. */
    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return sessaoRepository.findById(id).map(sessao -> paraAutorizacao(sessao, null)).orElse(null);
    }

    /**
     * Só os tokens que este armazenamento guarda: o refresh token (`ATIVA`) e o token de pré-autenticação (acesso,
     * `PRE_AUTENTICACAO`). Qualquer outro tipo, um token de acesso normal, um token expirado ou de outra audiência dá
     * {@code null}.
     */
    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tipoToken) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (tipoToken == null) {
            // Revogação sem `token_type_hint` (RFC 7009 §2.1): procura nos dois tipos guardados.
            OAuth2Authorization refresh = findByToken(token, OAuth2TokenType.REFRESH_TOKEN);
            return refresh != null ? refresh : findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        }
        String audiencia;
        String tipoSessao;
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tipoToken)) {
            audiencia = TokenService.AUDIENCIA_REFRESH_TOKEN;
            tipoSessao = ATIVA;
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tipoToken)) {
            audiencia = TokenService.AUDIENCIA_PRE_AUTENTICACAO;
            tipoSessao = PRE_AUTENTICACAO;
        } else {
            return null;
        }
        JWTClaimsSet claims;
        try {
            claims = tokenService.validarJwtProprio(token, audiencia);
        } catch (SessaoInvalidaException e) {
            return null;
        }
        Optional<Sessao> sessao = sessaoRepository.findById(claims.getJWTID());
        return sessao
                .filter(s -> tipoSessao.equals(s.getTipo()))
                .filter(s -> s.getExpiraEm().isAfter(Instant.now()))
                .filter(s -> s.getUtilizador().getId().toString().equals(claims.getSubject()))
                .map(s -> paraAutorizacao(s, token))
                .orElse(null);
    }

    private OAuth2Authorization paraAutorizacao(Sessao sessao, String valorToken) {
        RegisteredClient cliente = clientes.findById(RepositorioClientesSaigt.ID_CLIENTE_UI);
        Utilizador utilizador = sessao.getUtilizador();
        boolean pre = PRE_AUTENTICACAO.equals(sessao.getTipo());
        Authentication principal = UsernamePasswordAuthenticationToken.authenticated(
                utilizador.getId().toString(), null, List.of());
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(cliente)
                .id(sessao.getJti())
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(grantDe(sessao, utilizador))
                .attribute(Principal.class.getName(), principal)
                .attribute(TIPO_SESSAO, sessao.getTipo());
        if (sessao.getContextoSessao() != null) {
            builder.attribute(CONTEXTO_SESSAO, sessao.getContextoSessao());
        }
        boolean revogada = sessao.getRevogadoEm() != null;
        if (valorToken != null && pre) {
            OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, valorToken,
                    sessao.getEmitidoEm(), sessao.getExpiraEm());
            builder.token(token, metadados -> {
                metadados.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.<String, Object>of("jti", sessao.getJti(), "sub", utilizador.getId().toString()));
                metadados.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, revogada);
            });
        } else if (valorToken != null) {
            OAuth2RefreshToken token = new OAuth2RefreshToken(valorToken, sessao.getEmitidoEm(), sessao.getExpiraEm());
            builder.token(token, metadados -> metadados.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, revogada));
        }
        return builder.build();
    }

    /** A linha não guarda o grant: pré-autenticação é o do INCM; uma sessão ativa INCM vem da escolha de entidade. */
    private static AuthorizationGrantType grantDe(Sessao sessao, Utilizador utilizador) {
        if (PRE_AUTENTICACAO.equals(sessao.getTipo())) {
            return GrantsUtilizador.INCM_PASSWORD;
        }
        return ORIGEM_INCM.equals(utilizador.getOrigemAutenticacao())
                ? GrantsUtilizador.ENTIDADE : AuthorizationGrantType.REFRESH_TOKEN;
    }

    /** Só de tokens que o próprio servidor acabou de emitir (o `save` vem do grant); a validação é a do `findByToken`. */
    private static String jtiDe(String jwt) {
        try {
            String jti = SignedJWT.parse(jwt).getJWTClaimsSet().getJWTID();
            exigir(jti != null && !jti.isBlank(), "O token não tem jti.");
            return jti;
        } catch (ParseException e) {
            throw new IllegalArgumentException("O token não é um JWT.", e);
        }
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
