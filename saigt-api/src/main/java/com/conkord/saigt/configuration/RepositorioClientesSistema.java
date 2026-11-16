package com.conkord.saigt.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Clientes de sistema (client credentials) lidos do ambiente no arranque; o `id` do RegisteredClient é o nome
 * lógico do sistema e vai no claim `sistema` do token. Sem as duas variáveis o cliente não existe.
 */
public class RepositorioClientesSistema implements RegisteredClientRepository {

    private static final Logger log = LoggerFactory.getLogger(RepositorioClientesSistema.class);
    private static final Pattern CARATERES_PERMITIDOS = Pattern.compile("[A-Za-z0-9._-]+");

    private final Map<String, RegisteredClient> porId = new HashMap<>();
    private final Map<String, RegisteredClient> porClientId = new HashMap<>();

    public RepositorioClientesSistema(List<CredenciaisSistema> credenciais, Duration validadeToken) {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        for (CredenciaisSistema c : credenciais) {
            if (!validar(c)) {
                log.warn("Cliente OAuth não registado: variáveis em falta | sistema={}", c.sistema());
                continue;
            }
            if (porClientId.containsKey(c.clientId())) {
                throw new IllegalStateException(c.variavelClientId() + " repete o client id de outro sistema.");
            }
            RegisteredClient cliente = RegisteredClient.withId(c.sistema())
                    .clientId(c.clientId())
                    .clientName(c.sistema())
                    .clientSecret("{bcrypt}" + bcrypt.encode(c.clientSecret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                            .accessTokenTimeToLive(validadeToken)
                            .build())
                    .build();
            porId.put(cliente.getId(), cliente);
            porClientId.put(cliente.getClientId(), cliente);
            log.info("Cliente OAuth registado | sistema={}", c.sistema());
        }
    }

    /** Falha o arranque com o nome da variável, nunca com o valor. */
    private static boolean validar(CredenciaisSistema c) {
        boolean temId = c.clientId() != null && !c.clientId().isBlank();
        boolean temSegredo = c.clientSecret() != null && !c.clientSecret().isBlank();
        if (!temId && !temSegredo) {
            return false;
        }
        if (!temId) {
            throw new IllegalStateException(c.variavelClientId() + " em falta: " + c.variavelClientSecret() + " está definida.");
        }
        if (!temSegredo) {
            throw new IllegalStateException(c.variavelClientSecret() + " em falta: " + c.variavelClientId() + " está definida.");
        }
        if (!CARATERES_PERMITIDOS.matcher(c.clientId()).matches()) {
            throw new IllegalStateException(c.variavelClientId() + " tem caracteres fora de A-Z a-z 0-9 - . _");
        }
        if (!CARATERES_PERMITIDOS.matcher(c.clientSecret()).matches()) {
            throw new IllegalStateException(c.variavelClientSecret() + " tem caracteres fora de A-Z a-z 0-9 - . _");
        }
        return true;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Os clientes de sistema vêm só da configuração.");
    }

    @Override
    public RegisteredClient findById(String id) {
        return porId.get(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return porClientId.get(clientId);
    }

    public record CredenciaisSistema(String sistema, String variavelClientId, String clientId,
                                     String variavelClientSecret, String clientSecret) {

        @Override
        public String toString() {
            return "CredenciaisSistema[sistema=" + sistema + "]";
        }
    }
}
