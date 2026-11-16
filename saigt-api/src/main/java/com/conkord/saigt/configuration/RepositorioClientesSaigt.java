package com.conkord.saigt.configuration;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Os clientes de sistema mais o cliente público `saigt-ui`: sem segredo (nunca há segredos no browser), só os
 * grants de utilizador e `refresh_token`. O `id` do RegisteredClient é fixo, o `client_id` vem da configuração.
 */
public class RepositorioClientesSaigt implements RegisteredClientRepository {

    public static final String ID_CLIENTE_UI = "saigt-ui";
    private static final Pattern CARATERES_PERMITIDOS = Pattern.compile("[A-Za-z0-9._-]+");

    private final RegisteredClientRepository sistemas;
    private final RegisteredClient ui;

    public RepositorioClientesSaigt(RegisteredClientRepository sistemas, String clientIdUi, Duration validadeAcesso,
                                    Duration validadeRefresh) {
        if (clientIdUi == null || !CARATERES_PERMITIDOS.matcher(clientIdUi).matches()) {
            throw new IllegalStateException("SAIGT_OAUTH_UI_CLIENT_ID em falta ou com caracteres fora de A-Z a-z 0-9 - . _");
        }
        if (sistemas.findByClientId(clientIdUi) != null) {
            throw new IllegalStateException("SAIGT_OAUTH_UI_CLIENT_ID repete o client id de um sistema.");
        }
        this.sistemas = sistemas;
        this.ui = RegisteredClient.withId(ID_CLIENTE_UI)
                .clientId(clientIdUi)
                .clientName(ID_CLIENTE_UI)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(GrantsUtilizador.INCM_PASSWORD)
                .authorizationGrantType(GrantsUtilizador.ENTIDADE)
                .authorizationGrantType(GrantsUtilizador.AD_PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(validadeAcesso)
                        .refreshTokenTimeToLive(validadeRefresh)
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Os clientes vêm só da configuração.");
    }

    @Override
    public RegisteredClient findById(String id) {
        return ID_CLIENTE_UI.equals(id) ? ui : sistemas.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return ui.getClientId().equals(clientId) ? ui : sistemas.findByClientId(clientId);
    }
}
