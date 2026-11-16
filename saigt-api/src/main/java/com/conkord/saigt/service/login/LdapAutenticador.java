package com.conkord.saigt.service.login;

import com.conkord.saigt.exception.AdIndisponivelException;

/** Isola os logins da DGT da mecânica LDAP. */
public interface LdapAutenticador {

    /**
     * Grant `ad-password`: só confirma o acesso, o AD não devolve dados. {@code false} quando o AD recusa as
     * credenciais, sem distinguir username desconhecido de senha errada.
     *
     * @throws AdIndisponivelException quando o AD não responde ou a ligação falha
     */
    boolean confirmarAcesso(String username, String senha);
}
