package com.conkord.saigt.configuration;

import com.conkord.saigt.service.login.GrantRecusadoException;
import com.conkord.saigt.service.login.LoginAdGrantService;
import com.conkord.saigt.service.login.LoginConcluido;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/** Grant `ad-password`: login de utilizador da DGT pelo AD. A resposta leva o token de acesso e o refresh token. */
@Component
class AdPasswordAuthenticationProvider extends ProviderGrantUtilizador {

    static final String PARAMETRO_USERNAME = "username";
    static final String PARAMETRO_PASSWORD = "password";

    private final LoginAdGrantService servico;

    AdPasswordAuthenticationProvider(LoginAdGrantService servico) {
        super(GrantsUtilizador.AD_PASSWORD);
        this.servico = servico;
    }

    @Override
    protected Authentication executar(GrantUtilizadorAuthenticationToken pedido, OAuth2ClientAuthenticationToken cliente,
                                      RegisteredClient registado) {
        try {
            LoginConcluido concluido = servico.entrar(pedido.parametro(PARAMETRO_USERNAME),
                    pedido.parametro(PARAMETRO_PASSWORD), pedido.ipOrigem(), registado);
            OAuth2AccessToken acesso = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, concluido.accessToken(),
                    concluido.emitidoEm(), concluido.expiraEm());
            return new OAuth2AccessTokenAuthenticationToken(registado, cliente, acesso, concluido.refreshToken());
        } catch (GrantRecusadoException e) {
            throw new ErroGrantException(e.codigo(), e.getMessage());
        }
    }
}
