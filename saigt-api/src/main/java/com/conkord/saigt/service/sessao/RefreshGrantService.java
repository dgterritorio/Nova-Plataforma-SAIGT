package com.conkord.saigt.service.sessao;

import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.exception.SessaoInvalidaException;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.login.GrantRecusadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Grant `refresh_token` do `saigt-ui` (BL-IAM-004). Só uma linha `ATIVA`, não revogada e não expirada, roda: a linha
 * fica revogada e a nova herda o contexto e o `expira_em`, porque as 8 h contam desde o login. A linha é bloqueada
 * para que o mesmo refresh token, apresentado duas vezes ao mesmo tempo, só rode uma. A INCM não é chamada.
 */
@Service
public class RefreshGrantService {

    private static final Logger log = LoggerFactory.getLogger(RefreshGrantService.class);
    static final String MENSAGEM_REFRESH_INVALIDO = "Refresh token inválido, expirado ou já usado.";

    private final OAuth2AuthorizationService autorizacoes;
    private final SessaoRepository sessaoRepository;
    private final SessaoService sessaoService;
    private final EmissorTokensUtilizador emissorTokens;
    private final OAuth2TokenGenerator<? extends OAuth2Token> geradorTokens;
    private final TransactionTemplate transacao;

    public RefreshGrantService(OAuth2AuthorizationService autorizacoes, SessaoRepository sessaoRepository,
                               SessaoService sessaoService, EmissorTokensUtilizador emissorTokens,
                               OAuth2TokenGenerator<? extends OAuth2Token> geradorTokens,
                               PlatformTransactionManager transactionManager) {
        this.autorizacoes = autorizacoes;
        this.sessaoRepository = sessaoRepository;
        this.sessaoService = sessaoService;
        this.emissorTokens = emissorTokens;
        this.geradorTokens = geradorTokens;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public record RefreshConcluido(String accessToken, Instant emitidoEm, Instant expiraEm, OAuth2RefreshToken refreshToken) {
    }

    public RefreshConcluido rodar(String refreshToken, RegisteredClient cliente) {
        OAuth2Authorization autorizacao = autorizacoes.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
        if (autorizacao == null || !cliente.getId().equals(autorizacao.getRegisteredClientId())) {
            throw GrantRecusadoException.invalidGrant(MENSAGEM_REFRESH_INVALIDO);
        }
        RefreshConcluido concluido = transacao.execute(estado -> concluir(autorizacao.getId(), cliente));
        log.info("Refresh concluído | utilizadorId={}", autorizacao.getPrincipalName());
        return concluido;
    }

    private RefreshConcluido concluir(String jti, RegisteredClient cliente) {
        Instant agora = Instant.now();
        Sessao antiga = sessaoRepository.findByJtiComBloqueio(jti)
                .filter(sessao -> AutorizacoesSessao.ATIVA.equals(sessao.getTipo()))
                .filter(sessao -> sessao.getRevogadoEm() == null && sessao.getExpiraEm().isAfter(agora))
                .orElseThrow(() -> GrantRecusadoException.invalidGrant(MENSAGEM_REFRESH_INVALIDO));
        antiga.setRevogadoEm(agora);
        sessaoRepository.save(antiga);
        // A revogação tem de chegar à BD antes do INSERT da linha nova (índice de sessão única).
        sessaoRepository.flush();

        Utilizador utilizador = antiga.getUtilizador();
        String contexto = antiga.getContextoSessao();
        Jwt accessToken;
        try {
            accessToken = emissorTokens.emitirAcesso(sessaoService.claimsDaSessao(utilizador, contexto));
        } catch (SessaoInvalidaException e) {
            throw GrantRecusadoException.invalidGrant(MENSAGEM_REFRESH_INVALIDO);
        }
        OAuth2RefreshToken novoRefresh = gerarRefreshToken(cliente, utilizador, antiga.getExpiraEm());

        OAuth2Authorization.Builder nova = OAuth2Authorization.withRegisteredClient(cliente)
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.ATIVA)
                .refreshToken(novoRefresh);
        if (contexto != null) {
            nova.attribute(AutorizacoesSessao.CONTEXTO_SESSAO, contexto);
        }
        autorizacoes.save(nova.build());

        return new RefreshConcluido(accessToken.getTokenValue(), accessToken.getIssuedAt(), accessToken.getExpiresAt(),
                novoRefresh);
    }

    private OAuth2RefreshToken gerarRefreshToken(RegisteredClient cliente, Utilizador utilizador, Instant fimSessao) {
        OAuth2Token token = geradorTokens.generate(DefaultOAuth2TokenContext.builder()
                .registeredClient(cliente)
                .principal(UsernamePasswordAuthenticationToken.authenticated(utilizador.getId().toString(), null, List.of()))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .put(AutorizacoesSessao.FIM_SESSAO, fimSessao)
                .build());
        if (!(token instanceof OAuth2RefreshToken refreshToken)) {
            throw new IllegalStateException("O cliente não recebe refresh tokens.");
        }
        return refreshToken;
    }
}
