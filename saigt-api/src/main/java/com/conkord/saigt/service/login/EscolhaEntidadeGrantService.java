package com.conkord.saigt.service.login;

import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.configuration.GrantsUtilizador;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.sessao.AutorizacoesSessao;
import com.conkord.saigt.service.sessao.ContextoPreAutenticacao;
import com.conkord.saigt.service.sessao.ContextoSessaoAtiva;
import com.conkord.saigt.service.sessao.SessaoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Passo 2 do login INCM como grant `entidade` (BL-IAM-002). As recusas antes de qualquer alteração (adesão fora do
 * contexto, sem correspondência) não consomem a pré-autenticação. A conclusão (revogar a pré-autenticação e as
 * sessões ativas, gravar a nova, auditar) é uma só transação, com a linha de pré-autenticação bloqueada para que o
 * mesmo token não abra duas sessões.
 */
@Service
public class EscolhaEntidadeGrantService {

    private static final Logger log = LoggerFactory.getLogger(EscolhaEntidadeGrantService.class);
    static final String MENSAGEM_PRE_AUTENTICACAO_INVALIDA = "Pré-autenticação inválida, expirada ou já usada.";
    static final String MENSAGEM_ENTIDADE_INDISPONIVEL = "Entidade não disponível para este utilizador.";

    private final OAuth2AuthorizationService autorizacoes;
    private final UtilizadorRepository utilizadorRepository;
    private final CorrespondenciaEntidadeService correspondenciaEntidadeService;
    private final AdesoesIncm adesoesIncm;
    private final SessaoRepository sessaoRepository;
    private final SessaoService sessaoService;
    private final AuditoriaService auditoriaService;
    private final EmissorTokensUtilizador emissorTokens;
    private final SessaoAtivaGrant sessaoAtiva;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transacao;

    public EscolhaEntidadeGrantService(OAuth2AuthorizationService autorizacoes, UtilizadorRepository utilizadorRepository,
                                       CorrespondenciaEntidadeService correspondenciaEntidadeService,
                                       AdesoesIncm adesoesIncm, SessaoRepository sessaoRepository,
                                       SessaoService sessaoService, AuditoriaService auditoriaService,
                                       EmissorTokensUtilizador emissorTokens, SessaoAtivaGrant sessaoAtiva,
                                       ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.autorizacoes = autorizacoes;
        this.utilizadorRepository = utilizadorRepository;
        this.correspondenciaEntidadeService = correspondenciaEntidadeService;
        this.adesoesIncm = adesoesIncm;
        this.sessaoRepository = sessaoRepository;
        this.sessaoService = sessaoService;
        this.auditoriaService = auditoriaService;
        this.emissorTokens = emissorTokens;
        this.sessaoAtiva = sessaoAtiva;
        this.objectMapper = objectMapper;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public LoginConcluido escolher(String preAuthToken, String incmEnrollmentId, String ipOrigem, RegisteredClient cliente) {
        OAuth2Authorization preAutenticacao = autorizacoes.findByToken(preAuthToken, OAuth2TokenType.ACCESS_TOKEN);
        if (preAutenticacao == null || !cliente.getId().equals(preAutenticacao.getRegisteredClientId())
                || !AutorizacoesSessao.PRE_AUTENTICACAO.equals(preAutenticacao.getAttribute(AutorizacoesSessao.TIPO_SESSAO))
                || preAutenticacao.getAccessToken() == null || !preAutenticacao.getAccessToken().isActive()) {
            throw GrantRecusadoException.invalidGrant(MENSAGEM_PRE_AUTENTICACAO_INVALIDA);
        }
        ClaimsUtilizador.ClaimsEntidade adesao = lerContexto(preAutenticacao).entidades().stream()
                .filter(candidata -> incmEnrollmentId.equals(candidata.incmEnrollmentId()))
                .findFirst()
                .orElseThrow(() -> GrantRecusadoException.invalidGrant(MENSAGEM_ENTIDADE_INDISPONIVEL));

        Utilizador utilizador = utilizadorRepository.findById(UUID.fromString(preAutenticacao.getPrincipalName()))
                .orElseThrow(() -> GrantRecusadoException.invalidGrant(MENSAGEM_PRE_AUTENTICACAO_INVALIDA));
        Entidade entidade = correspondenciaEntidadeService.corresponder(adesao.nipc(), adesao.nome()).orElse(null);
        if (entidade == null) {
            adesoesIncm.registarRecusaSemCorrespondencia(utilizador, adesao.nome(), adesao.nipc(), ipOrigem);
            throw GrantRecusadoException.invalidGrant(AdesoesIncm.MENSAGEM_ENTIDADE_NAO_CRIADA);
        }

        ClaimsUtilizador.ClaimsEntidade claimsEntidade = ClaimsEntidadeMapper.comEntidade(adesao, entidade);
        Jwt accessToken = emissorTokens.emitirAcesso(new ClaimsUtilizador(utilizador.getId(), utilizador.getNome(),
                utilizador.getEmail(), utilizador.getOrigemAutenticacao(), null, claimsEntidade, utilizador.getCargo()));
        OAuth2RefreshToken refreshToken = sessaoAtiva.gerarRefreshToken(cliente, utilizador, GrantsUtilizador.ENTIDADE);

        transacao.executeWithoutResult(estado -> concluir(preAutenticacao.getId(), utilizador, entidade, claimsEntidade,
                cliente, refreshToken, ipOrigem));

        log.info("Login INCM concluído | utilizadorId={} entidadeId={}", utilizador.getId(), entidade.getId());
        return new LoginConcluido(accessToken.getTokenValue(), accessToken.getIssuedAt(), accessToken.getExpiresAt(),
                refreshToken);
    }

    private void concluir(String jtiPreAutenticacao, Utilizador utilizador, Entidade entidade,
                          ClaimsUtilizador.ClaimsEntidade claimsEntidade, RegisteredClient cliente,
                          OAuth2RefreshToken refreshToken, String ipOrigem) {
        Sessao linha = sessaoRepository.findByJtiComBloqueio(jtiPreAutenticacao).orElse(null);
        if (linha == null || linha.getRevogadoEm() != null || !linha.getExpiraEm().isAfter(Instant.now())) {
            throw GrantRecusadoException.invalidGrant(MENSAGEM_PRE_AUTENTICACAO_INVALIDA);
        }
        sessaoService.revogarPreAutenticacao(jtiPreAutenticacao);
        sessaoAtiva.abrir(cliente, utilizador, GrantsUtilizador.ENTIDADE,
                paraJson(new ContextoSessaoAtiva(claimsEntidade)), refreshToken);
        auditoriaService.registar(AdesoesIncm.TIPO_OCORRENCIA_UTILIZADOR, utilizador.getId().toString(),
                AdesoesIncm.ACAO_LOGIN, utilizador.getId(), entidade.getId(), ipOrigem, null);
    }

    private ContextoPreAutenticacao lerContexto(OAuth2Authorization preAutenticacao) {
        String json = preAutenticacao.getAttribute(AutorizacoesSessao.CONTEXTO_SESSAO);
        try {
            return objectMapper.readValue(json, ContextoPreAutenticacao.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw GrantRecusadoException.invalidGrant(MENSAGEM_PRE_AUTENTICACAO_INVALIDA);
        }
    }

    private String paraJson(Object contexto) {
        try {
            return objectMapper.writeValueAsString(contexto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o contexto de sessão.", e);
        }
    }
}
