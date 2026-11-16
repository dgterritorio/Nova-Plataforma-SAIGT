package com.conkord.saigt.service.login;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LdapAutenticadorSelecaoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(SpringLdapAutenticador.class, StubLdapAutenticador.class)
            .withPropertyValues("app.ldap.url=ldap://localhost:389", "app.ldap.base-dn=dc=saigt,dc=teste");

    @Test
    void oBeanAtivoEOCaminhoRealQuandoOMockNaoEstaAtivo() {
        contexto.run(ativo -> assertThat(ativo.getBean(LdapAutenticador.class))
                .isInstanceOf(SpringLdapAutenticador.class));
    }

    @Test
    void oBeanAtivoPassaASerOStubQuandoOMockEstaAtivo() {
        contexto.withPropertyValues("app.ldap-mock.enabled=true")
                .run(ativo -> assertThat(ativo.getBean(LdapAutenticador.class))
                        .isInstanceOf(StubLdapAutenticador.class));
    }
}
