package com.conkord.saigt.service.login;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubLdapAutenticadorTest {

    private final StubLdapAutenticador autenticador = new StubLdapAutenticador(new LdapMockProperties(List.of(
            new LdapMockProperties.UtilizadorMock("ana.silva", "Senha123!"),
            new LdapMockProperties.UtilizadorMock("carlos.tecnico", "Senha123!"))));

    @Test
    void deveConfirmarOAcessoPeloUsernameComAPasswordCerta() {
        assertThat(autenticador.confirmarAcesso("ana.silva", "Senha123!")).isTrue();
        assertThat(autenticador.confirmarAcesso("carlos.tecnico", "Senha123!")).isTrue();
    }

    @Test
    void deveRecusarOAcessoComPasswordErradaUsernameDesconhecidoOuPeloEmail() {
        assertThat(autenticador.confirmarAcesso("ana.silva", "password-errada")).isFalse();
        assertThat(autenticador.confirmarAcesso("desconhecido", "Senha123!")).isFalse();
        assertThat(autenticador.confirmarAcesso("ana.silva@dgt.pt", "Senha123!")).isFalse();
    }
}
