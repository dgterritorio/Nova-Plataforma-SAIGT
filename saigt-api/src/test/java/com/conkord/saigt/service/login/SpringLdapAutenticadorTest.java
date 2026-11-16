package com.conkord.saigt.service.login;

import ch.qos.logback.classic.Level;
import com.conkord.saigt.exception.AdIndisponivelException;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bind contra um UnboundID embebido. O UnboundID não aceita UPN nem nomes simples (só o AD os aceita): um intercetor
 * regista o principal que chegou ao servidor e troca-o pelo DN da entrada, para o bind validar a senha a sério.
 */
@ExtendWith(OutputCaptureExtension.class)
class SpringLdapAutenticadorTest {

    private static final String BASE = "dc=dgt,dc=pt";
    private static final String USERNAME = "ana.silva";
    private static final String SENHA = "senha-ficticia-do-teste";
    private static final String FORMATO_DN = "uid={0},ou=pessoas," + BASE;

    private static final List<String> principaisRecebidos = new CopyOnWriteArrayList<>();
    private static InMemoryDirectoryServer diretorio;

    @BeforeAll
    static void arrancarDiretorio() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
        config.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor() {
            @Override
            public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest pedido) {
                String principal = pedido.getRequest().getBindDN();
                principaisRecebidos.add(principal);
                if (!principal.isEmpty() && !principal.contains("=")) {
                    String uid = principal.contains("@") ? principal.substring(0, principal.indexOf('@')) : principal;
                    pedido.setRequest(new SimpleBindRequest("uid=" + uid + ",ou=pessoas," + BASE,
                            pedido.getRequest().getPassword().getValue()));
                }
            }
        });
        diretorio = new InMemoryDirectoryServer(config);
        diretorio.startListening();
        diretorio.add(new Entry("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: dgt"));
        diretorio.add(new Entry("dn: ou=pessoas," + BASE, "objectClass: top", "objectClass: organizationalUnit",
                "ou: pessoas"));
        diretorio.add(new Entry("dn: uid=" + USERNAME + ",ou=pessoas," + BASE, "objectClass: top",
                "objectClass: inetOrgPerson", "uid: " + USERNAME, "cn: Ana Ficticia", "sn: Ficticia",
                "mail: ana.silva@dgt.pt", "userPassword: " + SENHA));
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SpringLdapAutenticador.class)).setLevel(Level.DEBUG);
    }

    @AfterAll
    static void pararDiretorio() {
        diretorio.shutDown(true);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SpringLdapAutenticador.class)).setLevel(null);
    }

    @BeforeEach
    void limparPrincipais() {
        principaisRecebidos.clear();
    }

    private static SpringLdapAutenticador autenticador(String formatoBind) {
        return new SpringLdapAutenticador("ldap://localhost:" + diretorio.getListenPort(), BASE, formatoBind);
    }

    @Test
    void deveConfirmarOAcessoComASenhaCertaERecusarComAErrada() {
        SpringLdapAutenticador autenticador = autenticador(FORMATO_DN);

        assertThat(autenticador.confirmarAcesso(USERNAME, SENHA)).isTrue();
        assertThat(autenticador.confirmarAcesso(USERNAME, "senha-errada")).isFalse();
        assertThat(autenticador.confirmarAcesso("desconhecido", SENHA)).isFalse();
    }

    @Test
    void deveFazerOBindComOFormatoConfiguradoUpnNomeSimplesOuDn() {
        assertThat(autenticador("{0}@dgt.pt").confirmarAcesso(USERNAME, SENHA)).isTrue();
        assertThat(autenticador("{0}").confirmarAcesso(USERNAME, SENHA)).isTrue();
        assertThat(autenticador(FORMATO_DN).confirmarAcesso(USERNAME, SENHA)).isTrue();

        assertThat(principaisRecebidos).containsExactly("ana.silva@dgt.pt", "ana.silva",
                "uid=ana.silva,ou=pessoas,dc=dgt,dc=pt");
    }

    @Test
    void deveEscaparOUsernameQuandoOFormatoEUmDn() {
        assertThat(autenticador(FORMATO_DN).principalDoBind("ana,ou=admins"))
                .isEqualTo("uid=ana\\,ou\\=admins,ou=pessoas,dc=dgt,dc=pt");
        assertThat(autenticador("{0}@dgt.pt").principalDoBind("ana.silva")).isEqualTo("ana.silva@dgt.pt");
    }

    @Test
    void deveRecusarSemContactarOServidorQuandoASenhaEVazia() {
        assertThat(autenticador(FORMATO_DN).confirmarAcesso(USERNAME, "")).isFalse();
        assertThat(principaisRecebidos).isEmpty();
    }

    @Test
    void deveRecusarUmFormatoSemOLugarDoUsername() {
        assertThatThrownBy(() -> autenticador("ana.silva@dgt.pt")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarAdIndisponivelQuandoOServidorEstaInacessivel() {
        SpringLdapAutenticador comServidorErrado = new SpringLdapAutenticador("ldap://localhost:1", BASE, "{0}@dgt.pt");

        assertThatThrownBy(() -> comServidorErrado.confirmarAcesso(USERNAME, SENHA))
                .isInstanceOf(AdIndisponivelException.class);
    }

    @Test
    void osLogsDosLoginsCertosErradosEFalhadosNaoTemEmailUsernameNemSenha(CapturedOutput saida) {
        SpringLdapAutenticador autenticador = autenticador("{0}@dgt.pt");
        autenticador.confirmarAcesso(USERNAME, SENHA);
        autenticador.confirmarAcesso(USERNAME, "senha-errada");
        SpringLdapAutenticador comServidorErrado = new SpringLdapAutenticador("ldap://localhost:1", BASE, "{0}@dgt.pt");
        assertThatThrownBy(() -> comServidorErrado.confirmarAcesso(USERNAME, SENHA))
                .isInstanceOf(AdIndisponivelException.class);

        assertThat(saida.getAll())
                .contains("Bind LDAP aceite", "Bind LDAP recusado", "Falha ao comunicar com o AD da DGT")
                .doesNotContain(USERNAME, "ana.silva@dgt.pt", SENHA, "senha-errada");
    }
}
