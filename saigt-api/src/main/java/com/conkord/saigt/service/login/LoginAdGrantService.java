package com.conkord.saigt.service.login;

import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.configuration.GrantsUtilizador;
import com.conkord.saigt.exception.AdIndisponivelException;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Login de utilizador da DGT como grant `ad-password` (BL-IAM-003). O AD só confirma o acesso: a conta `AD_DGT`
 * corresponde-se pelo `nome`, que é o username, e nasce `VISITANTE` no primeiro login; nenhum dado da conta é
 * alterado. Os logs só levam o id do utilizador, nunca o username, o email nem a senha.
 */
@Service
public class LoginAdGrantService {

    private static final Logger log = LoggerFactory.getLogger(LoginAdGrantService.class);
    static final String MENSAGEM_CREDENCIAIS = "Credenciais inválidas.";
    static final String MENSAGEM_AD_INDISPONIVEL = "Serviço de autenticação da DGT temporariamente indisponível.";
    static final String ORIGEM_AD_DGT = "AD_DGT";
    private static final String ORIGEM_INCM = "INCM";
    private static final String ACAO_CRIAR = "CRIAR";

    private final LdapAutenticador ldapAutenticador;
    private final UtilizadorRepository utilizadorRepository;
    private final AuditoriaService auditoriaService;
    private final EmissorTokensUtilizador emissorTokens;
    private final SessaoAtivaGrant sessaoAtiva;
    private final TransactionTemplate transacao;

    public LoginAdGrantService(LdapAutenticador ldapAutenticador, UtilizadorRepository utilizadorRepository,
                               AuditoriaService auditoriaService, EmissorTokensUtilizador emissorTokens,
                               SessaoAtivaGrant sessaoAtiva,
                               PlatformTransactionManager transactionManager) {
        this.ldapAutenticador = ldapAutenticador;
        this.utilizadorRepository = utilizadorRepository;
        this.auditoriaService = auditoriaService;
        this.emissorTokens = emissorTokens;
        this.sessaoAtiva = sessaoAtiva;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public LoginConcluido entrar(String username, String password, String ipOrigem, RegisteredClient cliente) {
        // O login INCM entra pelo email: com ele, as credenciais INCM nunca seguem para o AD.
        if (utilizadorRepository.existsByEmailAndOrigemAutenticacao(username, ORIGEM_INCM)) {
            log.info("Login AD recusado | motivo=CONTA_INCM");
            throw GrantRecusadoException.invalidGrant(MENSAGEM_CREDENCIAIS);
        }
        boolean aceite = confirmarNoAd(username, password);
        Optional<Utilizador> conta = utilizadorRepository.findByNomeAndOrigemAutenticacao(username, ORIGEM_AD_DGT);
        if (!aceite) {
            // Só quem já tem conta AD_DGT fica auditado (`auditoria` exige `utilizador_id`); a resposta é igual.
            conta.ifPresentOrElse(c -> auditarRecusa(c, "CREDENCIAIS_RECUSADAS_PELO_AD", ipOrigem),
                    () -> log.info("Login AD recusado | motivo=CREDENCIAIS_RECUSADAS_PELO_AD conta=inexistente"));
            throw GrantRecusadoException.invalidGrant(MENSAGEM_CREDENCIAIS);
        }

        return transacao.execute(estado -> concluir(conta, username, cliente, ipOrigem));
    }

    private boolean confirmarNoAd(String username, String password) {
        try {
            return ldapAutenticador.confirmarAcesso(username, password);
        } catch (AdIndisponivelException e) {
            log.warn("Login AD falhou: AD indisponível");
            throw new GrantRecusadoException(GrantRecusadoException.SERVER_ERROR, MENSAGEM_AD_INDISPONIVEL);
        }
    }

    private LoginConcluido concluir(Optional<Utilizador> conta, String username, RegisteredClient cliente,
                                    String ipOrigem) {
        Utilizador utilizador = conta.orElseGet(() -> criarVisitante(username, ipOrigem));
        Jwt accessToken = emissorTokens.emitirAcesso(new ClaimsUtilizador(utilizador.getId(), utilizador.getNome(),
                utilizador.getEmail(), utilizador.getOrigemAutenticacao(), utilizador.getPerfilInterno(), null, null));
        OAuth2RefreshToken refreshToken = sessaoAtiva.gerarRefreshToken(cliente, utilizador, GrantsUtilizador.AD_PASSWORD);

        sessaoAtiva.abrir(cliente, utilizador, GrantsUtilizador.AD_PASSWORD, null, refreshToken);
        auditoriaService.registar(AdesoesIncm.TIPO_OCORRENCIA_UTILIZADOR, utilizador.getId().toString(),
                AdesoesIncm.ACAO_LOGIN, utilizador.getId(), null, ipOrigem, null);

        log.info("Login AD concluído | utilizadorId={}", utilizador.getId());
        return new LoginConcluido(accessToken.getTokenValue(), accessToken.getIssuedAt(), accessToken.getExpiresAt(),
                refreshToken);
    }

    private Utilizador criarVisitante(String username, String ipOrigem) {
        Utilizador novo = new Utilizador(username, null);
        novo.setPerfilInterno(PerfilInterno.VISITANTE);
        novo = utilizadorRepository.save(novo);
        auditoriaService.registar(AdesoesIncm.TIPO_OCORRENCIA_UTILIZADOR, novo.getId().toString(), ACAO_CRIAR,
                novo.getId(), ipOrigem, Map.of("perfilInterno", PerfilInterno.VISITANTE.name(), "origem", "PRIMEIRO_LOGIN"));
        log.info("Conta AD_DGT criada no primeiro login | utilizadorId={}", novo.getId());
        return novo;
    }

    private void auditarRecusa(Utilizador conta, String motivo, String ipOrigem) {
        auditoriaService.registar(AdesoesIncm.TIPO_OCORRENCIA_UTILIZADOR, conta.getId().toString(),
                AdesoesIncm.ACAO_LOGIN_RECUSADO, conta.getId(), ipOrigem, Map.of("motivo", motivo));
        log.info("Login AD recusado | utilizadorId={} motivo={}", conta.getId(), motivo);
    }
}
