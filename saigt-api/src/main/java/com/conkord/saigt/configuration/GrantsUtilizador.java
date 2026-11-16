package com.conkord.saigt.configuration;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/** Extension grants (RFC 6749 §4.5) do login de utilizador: INCM em dois passos e AD da DGT. */
public final class GrantsUtilizador {

    public static final AuthorizationGrantType INCM_PASSWORD =
            new AuthorizationGrantType("urn:saigt:params:oauth:grant-type:incm-password");
    public static final AuthorizationGrantType ENTIDADE =
            new AuthorizationGrantType("urn:saigt:params:oauth:grant-type:entidade");
    public static final AuthorizationGrantType AD_PASSWORD =
            new AuthorizationGrantType("urn:saigt:params:oauth:grant-type:ad-password");

    private GrantsUtilizador() {
    }
}
