package com.conkord.saigt.service.login;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Utilizadores fixos do {@link StubLdapAutenticador}; só é bean com `app.ldap-mock.enabled=true`. */
@ConfigurationProperties(prefix = "app.ldap-mock")
public record LdapMockProperties(List<UtilizadorMock> users) {

    public record UtilizadorMock(String username, String password) {
    }
}
