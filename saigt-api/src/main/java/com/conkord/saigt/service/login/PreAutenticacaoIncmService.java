package com.conkord.saigt.service.login;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.incm.AdesaoIncm;
import com.conkord.saigt.integracao.incm.IncmAutenticador;
import com.conkord.saigt.integracao.incm.ResultadoAutenticacaoIncm;
import com.conkord.saigt.integracao.incm.UtilizadorIncm;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.sessao.AutorizacoesSessao;
import com.conkord.saigt.service.sessao.ContextoPreAutenticacao;
import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.configuration.GrantsUtilizador;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Passo 1 do login INCM como grant `incm-password` (BL-IAM-001): valida na INCM, sincroniza utilizador e
 * entidades e grava a `sessao` `PRE_AUTENTICACAO`. Não revoga a sessão ativa anterior (isso é na escolha) e não cria
 * entidades. Os logs só levam o id do utilizador, nunca email, nome, NIPC nem password.
 */
@Service
public class PreAutenticacaoIncmService {

    private static final Logger log = LoggerFactory.getLogger(PreAutenticacaoIncmService.class);
    static final String MENSAGEM_SEM_ENTIDADE = "Utilizador sem entidade associada na INCM.";
    private static final String MENSAGEM_CREDENCIAIS = "Credenciais inválidas.";
    private static final String ORIGEM_INCM = "INCM";

    private final IncmAutenticador incmAutenticador;
    private final UtilizadorRepository utilizadorRepository;
    private final AdesoesIncm adesoesIncm;
    private final UtilizadorEntidadeService utilizadorEntidadeService;
    private final EmissorTokensUtilizador emissorTokens;
    private final OAuth2AuthorizationService autorizacoes;
    private final ObjectMapper objectMapper;

    public PreAutenticacaoIncmService(IncmAutenticador incmAutenticador, UtilizadorRepository utilizadorRepository,
                                      AdesoesIncm adesoesIncm, UtilizadorEntidadeService utilizadorEntidadeService,
                                      EmissorTokensUtilizador emissorTokens, OAuth2AuthorizationService autorizacoes,
                                      ObjectMapper objectMapper) {
        this.incmAutenticador = incmAutenticador;
        this.utilizadorRepository = utilizadorRepository;
        this.adesoesIncm = adesoesIncm;
        this.utilizadorEntidadeService = utilizadorEntidadeService;
        this.emissorTokens = emissorTokens;
        this.autorizacoes = autorizacoes;
        this.objectMapper = objectMapper;
    }

    /** `nipc` é o `PublishingEntityTaxId` da adesão. */
    public record EntidadeAdesao(String incmEnrollmentId, String nipc, String nome) {
    }

    public record PreAutenticacaoEmitida(String token, Instant emitidoEm, Instant expiraEm,
                                         List<EntidadeAdesao> entidades) {
    }

    public PreAutenticacaoEmitida iniciar(String username, String password, String ipOrigem, RegisteredClient cliente) {
        ResultadoAutenticacaoIncm resultado = autenticarNaIncm(username, password);
        if (resultado.utilizador().isEmpty()) {
            // Só quem já tem conta INCM fica auditado (`auditoria` exige `utilizador_id`); a resposta é igual nos dois casos.
            utilizadorRepository.findByEmailAndOrigemAutenticacao(username, ORIGEM_INCM)
                    .ifPresentOrElse(conta -> adesoesIncm.auditarRecusa(conta, "CREDENCIAIS_RECUSADAS_PELA_INCM", ipOrigem),
                            () -> log.info("Login INCM recusado | motivo=CREDENCIAIS_RECUSADAS_PELA_INCM conta=inexistente"));
            String motivo = resultado.motivoRecusa();
            throw GrantRecusadoException.invalidGrant(motivo == null || motivo.isBlank() ? MENSAGEM_CREDENCIAIS : motivo);
        }

        UtilizadorIncm incm = resultado.utilizador().get();
        Utilizador utilizador = adesoesIncm.upsertUtilizador(incm);
        if (incm.adesoes().isEmpty()) {
            utilizadorEntidadeService.sincronizar(utilizador, List.of());
            adesoesIncm.auditarRecusa(utilizador, "SEM_ADESOES", ipOrigem);
            log.info("Login INCM recusado | utilizadorId={} motivo=SEM_ADESOES", utilizador.getId());
            throw GrantRecusadoException.invalidGrant(MENSAGEM_SEM_ENTIDADE);
        }

        List<Entidade> entidades = adesoesIncm.atualizarEntidades(incm.adesoes());
        // As adesões com entidade SAIGT, não só a que vier a ser escolhida: é a projeção de `utilizador_entidade`.
        utilizadorEntidadeService.sincronizar(utilizador, entidades);

        ContextoPreAutenticacao contexto = new ContextoPreAutenticacao(
                incm.adesoes().stream().map(ClaimsEntidadeMapper::deAdesao).toList());
        Jwt preAuth = emissorTokens.emitirPreAutenticacao(utilizador.getId());
        Instant emitidoEm = preAuth.getIssuedAt();
        Instant expiraEm = preAuth.getExpiresAt();

        autorizacoes.save(OAuth2Authorization.withRegisteredClient(cliente)
                .principalName(utilizador.getId().toString())
                .authorizationGrantType(GrantsUtilizador.INCM_PASSWORD)
                .attribute(AutorizacoesSessao.TIPO_SESSAO, AutorizacoesSessao.PRE_AUTENTICACAO)
                .attribute(AutorizacoesSessao.CONTEXTO_SESSAO, paraJson(contexto))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, preAuth.getTokenValue(), emitidoEm, expiraEm))
                .build());

        log.info("Pré-autenticação INCM emitida | utilizadorId={} adesoes={}", utilizador.getId(), incm.adesoes().size());
        return new PreAutenticacaoEmitida(preAuth.getTokenValue(), emitidoEm, expiraEm, incm.adesoes().stream()
                .map(PreAutenticacaoIncmService::paraEntidadeAdesao).toList());
    }

    private ResultadoAutenticacaoIncm autenticarNaIncm(String username, String password) {
        try {
            return Optional.ofNullable(incmAutenticador.autenticarComMotivo(username, password))
                    .orElseThrow(() -> new IllegalStateException("A INCM não devolveu resultado."));
        } catch (IncmIndisponivelException e) {
            log.warn("Login INCM falhou: INCM indisponível");
            throw new GrantRecusadoException(GrantRecusadoException.SERVER_ERROR,
                    "Serviço da INCM temporariamente indisponível.");
        }
    }

    private static EntidadeAdesao paraEntidadeAdesao(AdesaoIncm adesao) {
        return new EntidadeAdesao(adesao.incmEnrollmentId(), adesao.nif(), adesao.nomeEntidade());
    }

    private String paraJson(Object contexto) {
        try {
            return objectMapper.writeValueAsString(contexto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o contexto de sessão.", e);
        }
    }
}
