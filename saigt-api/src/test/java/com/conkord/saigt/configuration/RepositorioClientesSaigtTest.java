package com.conkord.saigt.configuration;

import com.conkord.saigt.configuration.RepositorioClientesSistema.CredenciaisSistema;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorioClientesSaigtTest {

    private static final Duration QUINZE_MINUTOS = Duration.ofMinutes(15);
    private static final Duration OITO_HORAS = Duration.ofHours(8);

    private static RepositorioClientesSistema sistemas() {
        return new RepositorioClientesSistema(List.of(new CredenciaisSistema("snit", "SAIGT_OAUTH_SNIT_CLIENT_ID",
                "snit-teste", "SAIGT_OAUTH_SNIT_CLIENT_SECRET", "segredo-ficticio-snit-0123456789")), QUINZE_MINUTOS);
    }

    @Test
    void deveRegistarOSaigtUiSemSegredoSoComOsGrantsDeUtilizadorERefreshToken() {
        RepositorioClientesSaigt repositorio = new RepositorioClientesSaigt(sistemas(), "saigt-ui", QUINZE_MINUTOS, OITO_HORAS);

        RegisteredClient ui = repositorio.findByClientId("saigt-ui");

        assertThat(ui).isNotNull();
        assertThat(repositorio.findById(RepositorioClientesSaigt.ID_CLIENTE_UI)).isSameAs(ui);
        assertThat(ui.getClientSecret()).isNull();
        assertThat(ui.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(ui.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(GrantsUtilizador.INCM_PASSWORD,
                GrantsUtilizador.ENTIDADE, GrantsUtilizador.AD_PASSWORD, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(ui.getAuthorizationGrantTypes()).doesNotContain(AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(ui.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(QUINZE_MINUTOS);
        assertThat(ui.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(OITO_HORAS);
    }

    @Test
    void deveManterOsClientesDeSistema() {
        RepositorioClientesSaigt repositorio = new RepositorioClientesSaigt(sistemas(), "saigt-ui", QUINZE_MINUTOS, OITO_HORAS);

        assertThat(repositorio.findByClientId("snit-teste").getId()).isEqualTo("snit");
        assertThat(repositorio.findById("snit")).isNotNull();
        assertThat(repositorio.findByClientId("desconhecido")).isNull();
    }

    @Test
    void deveUsarOClientIdConfigurado() {
        RepositorioClientesSaigt repositorio = new RepositorioClientesSaigt(sistemas(), "ui-staging", QUINZE_MINUTOS, OITO_HORAS);

        assertThat(repositorio.findByClientId("ui-staging")).isNotNull();
        assertThat(repositorio.findByClientId("saigt-ui")).isNull();
    }

    @Test
    void deveFalharOArranqueComClientIdRepetidoOuInvalido() {
        assertThatThrownBy(() -> new RepositorioClientesSaigt(sistemas(), "snit-teste", QUINZE_MINUTOS, OITO_HORAS))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SAIGT_OAUTH_UI_CLIENT_ID");
        assertThatThrownBy(() -> new RepositorioClientesSaigt(sistemas(), " ", QUINZE_MINUTOS, OITO_HORAS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RepositorioClientesSaigt(sistemas(), "ui teste", QUINZE_MINUTOS, OITO_HORAS))
                .isInstanceOf(IllegalStateException.class);
    }
}
