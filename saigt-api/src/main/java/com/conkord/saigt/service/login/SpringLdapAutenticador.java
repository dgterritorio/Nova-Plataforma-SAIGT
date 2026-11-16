package com.conkord.saigt.service.login;

import com.conkord.saigt.exception.AdIndisponivelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Component;

import javax.naming.directory.DirContext;

/**
 * Bind direto, sem conta de serviço. `LdapContextSource` nova por tentativa e `pooled=false` para nunca reaproveitar
 * uma ligação autenticada como outro utilizador. Os logs nunca levam o username nem a senha: só o resultado
 * e a causa técnica.
 */
@Component
@Slf4j
public class SpringLdapAutenticador implements LdapAutenticador {

    static final String MARCADOR_USERNAME = "{0}";

    private final String url;
    private final String baseDn;
    private final String formatoBind;

    public SpringLdapAutenticador(@Value("${app.ldap.url}") String url,
                                   @Value("${app.ldap.base-dn}") String baseDn,
                                   @Value("${app.ldap.bind-format:{0}}") String formatoBind) {
        if (formatoBind == null || !formatoBind.contains(MARCADOR_USERNAME)) {
            throw new IllegalStateException("LDAP_BIND_FORMAT tem de conter {0}, o lugar do username.");
        }
        this.url = url;
        this.baseDn = baseDn;
        this.formatoBind = formatoBind;
    }

    @Override
    public boolean confirmarAcesso(String username, String senha) {
        if (username == null || username.isBlank() || senha == null || senha.isEmpty()) {
            // Um bind com senha vazia é um bind anónimo, que o servidor aceita.
            return false;
        }
        DirContext ligacao = null;
        try {
            ligacao = contexto(principalDoBind(username), senha).getReadOnlyContext();
            log.debug("Bind LDAP aceite");
            return true;
        } catch (org.springframework.ldap.AuthenticationException e) {
            log.debug("Bind LDAP recusado");
            return false;
        } catch (NamingException e) {
            log.error("Falha ao comunicar com o AD da DGT | causa={}", causa(e));
            throw new AdIndisponivelException("AD da DGT indisponível.");
        } finally {
            LdapUtils.closeContext(ligacao);
        }
    }

    /** O formato vem de `LDAP_BIND_FORMAT`: UPN (`{0}@dominio`), nome simples (`{0}`) ou um DN, que se escapa. */
    String principalDoBind(String username) {
        String valor = formatoBind.contains("=") ? LdapEncoder.nameEncode(username) : username;
        return formatoBind.replace(MARCADOR_USERNAME, valor);
    }

    private LdapContextSource contexto(String principal, String senha) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(url);
        contextSource.setBase(baseDn);
        contextSource.setUserDn(principal);
        contextSource.setPassword(senha);
        contextSource.setPooled(false);
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    /** Só a classe da exceção: a mensagem do servidor pode repetir o principal do bind. */
    private static String causa(NamingException e) {
        Throwable raiz = e.getRootCause() != null ? e.getRootCause() : e;
        return raiz.getClass().getSimpleName();
    }
}
