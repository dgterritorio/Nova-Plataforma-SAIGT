package com.conkord.saigt.configuration;

import com.conkord.saigt.service.login.EscolhaEntidadeGrantService;
import com.conkord.saigt.service.login.GrantRecusadoException;
import com.conkord.saigt.service.login.LoginConcluido;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/** Grant `entidade`: o token de pré-autenticação só é aceite aqui. A resposta leva o token de acesso e o refresh token. */
@Component
class EntidadeAuthenticationProvider extends ProviderGrantUtilizador {

    static final String PARAMETRO_PRE_AUTH_TOKEN = "pre_auth_token";
    static final String PARAMETRO_INCM_ENROLLMENT_ID = "incm_enrollment_id";

    private final EscolhaEntidadeGrantService servico;

    EntidadeAuthenticationProvider(EscolhaEntidadeGrantService servico) {
        super(GrantsUtilizador.ENTIDADE);
        this.servico = servico;
    }

    @Override
    protected Authentication executar(GrantUtilizadorAuthenticationToken pedido, OAuth2ClientAuthenticationToken cliente,
                                      RegisteredClient registado) {
        try {
            LoginConcluido concluido = servico.escolher(
                    pedido.parametro(PARAMETRO_PRE_AUTH_TOKEN), pedido.parametro(PARAMETRO_INCM_ENROLLMENT_ID),
                    pedido.ipOrigem(), registado);
            OAuth2AccessToken acesso = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, concluido.accessToken(),
                    concluido.emitidoEm(), concluido.expiraEm());
            return new OAuth2AccessTokenAuthenticationToken(registado, cliente, acesso, concluido.refreshToken());
        } catch (GrantRecusadoException e) {
            throw new ErroGrantException(e.codigo(), e.getMessage());
        }
    }
}
