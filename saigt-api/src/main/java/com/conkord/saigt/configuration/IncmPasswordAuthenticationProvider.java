package com.conkord.saigt.configuration;

import com.conkord.saigt.service.login.GrantRecusadoException;
import com.conkord.saigt.service.login.PreAutenticacaoIncmService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Grant `incm-password`: a resposta é o token de pré-autenticação e as adesões (`entidades`), sem refresh token. */
@Component
class IncmPasswordAuthenticationProvider extends ProviderGrantUtilizador {

    static final String PARAMETRO_USERNAME = "username";
    static final String PARAMETRO_PASSWORD = "password";

    private final PreAutenticacaoIncmService servico;

    IncmPasswordAuthenticationProvider(PreAutenticacaoIncmService servico) {
        super(GrantsUtilizador.INCM_PASSWORD);
        this.servico = servico;
    }

    @Override
    protected Authentication executar(GrantUtilizadorAuthenticationToken pedido, OAuth2ClientAuthenticationToken cliente,
                                      RegisteredClient registado) {
        try {
            PreAutenticacaoIncmService.PreAutenticacaoEmitida emitida = servico.iniciar(
                    pedido.parametro(PARAMETRO_USERNAME), pedido.parametro(PARAMETRO_PASSWORD), pedido.ipOrigem(), registado);
            OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, emitida.token(),
                    emitida.emitidoEm(), emitida.expiraEm());
            return new OAuth2AccessTokenAuthenticationToken(registado, cliente, token, null,
                    Map.of("entidades", emitida.entidades()));
        } catch (GrantRecusadoException e) {
            throw new ErroGrantException(e.codigo(), e.getMessage());
        }
    }
}
