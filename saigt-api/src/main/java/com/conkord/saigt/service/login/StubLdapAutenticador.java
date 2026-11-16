package com.conkord.saigt.service.login;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Stub para desenvolvimento local sem AD. `@Primary` sobrepõe-se ao {@link SpringLdapAutenticador} quando ativo. */
@Component
@Primary
@Slf4j
@ConditionalOnProperty(name = "app.ldap-mock.enabled", havingValue = "true")
@EnableConfigurationProperties(LdapMockProperties.class)
public class StubLdapAutenticador implements LdapAutenticador {

    private final LdapMockProperties propriedades;

    public StubLdapAutenticador(LdapMockProperties propriedades) {
        this.propriedades = propriedades;
        log.warn("app.ldap-mock.enabled=true — login LDAP a usar utilizadores fixos de desenvolvimento. NUNCA usar em produção.");
    }

    @Override
    public boolean confirmarAcesso(String username, String senha) {
        return propriedades.users().stream()
                .anyMatch(utilizador -> utilizador.username() != null && utilizador.username().equals(username)
                        && utilizador.password().equals(senha));
    }
}
