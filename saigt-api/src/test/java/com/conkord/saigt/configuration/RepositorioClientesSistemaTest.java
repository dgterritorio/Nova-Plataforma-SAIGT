package com.conkord.saigt.configuration;

import com.conkord.saigt.configuration.RepositorioClientesSistema.CredenciaisSistema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RepositorioClientesSistemaTest {

    private static final Duration QUINZE_MINUTOS = Duration.ofMinutes(15);
    private static final String SEGREDO_SNIT = "segredo-ficticio-snit-0123456789";
    private static final String SEGREDO_GEOCONFORM = "segredo-ficticio-geoconform-0123456789";

    @Test
    void deveRegistarOsDoisClientesSoComClientCredentialsESegredoEmBcrypt() {
        RepositorioClientesSistema repositorio = new RepositorioClientesSistema(List.of(
                snit("snit-teste", SEGREDO_SNIT), geoconform("geoconform-teste", SEGREDO_GEOCONFORM)), QUINZE_MINUTOS);

        RegisteredClient snit = repositorio.findByClientId("snit-teste");
        assertThat(snit.getId()).isEqualTo("snit");
        assertThat(repositorio.findById("geoconform").getClientId()).isEqualTo("geoconform-teste");
        assertThat(snit.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(snit.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(snit.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(QUINZE_MINUTOS);
        assertThat(snit.getClientSecret()).startsWith("{bcrypt}").doesNotContain(SEGREDO_SNIT);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(SEGREDO_SNIT, snit.getClientSecret()))
                .isTrue();
    }

    @Test
    void naoDeveRegistarClienteSemVariaveisEAvisarSemSegredos(CapturedOutput output) {
        RepositorioClientesSistema repositorio = new RepositorioClientesSistema(List.of(
                snit("snit-teste", SEGREDO_SNIT), geoconform("", "")), QUINZE_MINUTOS);

        assertThat(repositorio.findById("geoconform")).isNull();
        assertThat(repositorio.findById("snit")).isNotNull();
        assertThat(output.getAll())
                .contains("Cliente OAuth não registado", "sistema=geoconform")
                .doesNotContain(SEGREDO_SNIT);
    }

    @Test
    void deveFalharOArranqueComSoUmaDasVariaveisSemMostrarOValor() {
        assertThatThrownBy(() -> new RepositorioClientesSistema(List.of(snit("snit-teste", null)), QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAIGT_OAUTH_SNIT_CLIENT_SECRET")
                .hasMessageNotContaining("snit-teste");
        assertThatThrownBy(() -> new RepositorioClientesSistema(List.of(snit(" ", SEGREDO_SNIT)), QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAIGT_OAUTH_SNIT_CLIENT_ID")
                .hasMessageNotContaining(SEGREDO_SNIT);
    }

    @Test
    void deveFalharOArranqueComCaracteresNaoPermitidosSemMostrarOValor() {
        String segredoInvalido = "segredo:com/caracteres";

        assertThatThrownBy(() -> new RepositorioClientesSistema(List.of(snit("snit-teste", segredoInvalido)), QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAIGT_OAUTH_SNIT_CLIENT_SECRET")
                .hasMessageNotContaining(segredoInvalido);
        assertThatThrownBy(() -> new RepositorioClientesSistema(List.of(snit("snit teste", SEGREDO_SNIT)), QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAIGT_OAUTH_SNIT_CLIENT_ID");
    }

    @Test
    void deveFalharOArranqueQuandoOsDoisSistemasPartilhamOClientId() {
        assertThatThrownBy(() -> new RepositorioClientesSistema(List.of(
                snit("mesmo-id", SEGREDO_SNIT), geoconform("mesmo-id", SEGREDO_GEOCONFORM)), QUINZE_MINUTOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAIGT_OAUTH_GEOCONFORM_CLIENT_ID");
    }

    @Test
    void naoDeveExporOSegredoNoToStringDasCredenciais() {
        assertThat(snit("snit-teste", SEGREDO_SNIT).toString()).doesNotContain(SEGREDO_SNIT);
    }

    private static CredenciaisSistema snit(String clientId, String clientSecret) {
        return new CredenciaisSistema("snit", "SAIGT_OAUTH_SNIT_CLIENT_ID", clientId,
                "SAIGT_OAUTH_SNIT_CLIENT_SECRET", clientSecret);
    }

    private static CredenciaisSistema geoconform(String clientId, String clientSecret) {
        return new CredenciaisSistema("geoconform", "SAIGT_OAUTH_GEOCONFORM_CLIENT_ID", clientId,
                "SAIGT_OAUTH_GEOCONFORM_CLIENT_SECRET", clientSecret);
    }
}
