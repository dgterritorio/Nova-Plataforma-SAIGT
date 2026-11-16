package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.configuration.NotificacoesEmailProperties;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CopiaEmailNotificacaoListenerTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");
    private static final AtomicLong IDS = new AtomicLong(7000);

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final EntidadeContactoRepository contactoRepository = mock(EntidadeContactoRepository.class);
    private final EnvioEmail envioEmail = mock(EnvioEmail.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CopiaEmailNotificacaoListener.class);

    private final Utilizador autorMaster = utilizador("Marta Master", "marta@dgt.exemplo.pt");

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(null);
    }

    private CopiaEmailNotificacaoListener listener(boolean ativo) {
        return new CopiaEmailNotificacaoListener(notificacaoRepository, contactoRepository, envioEmail,
                new NotificacoesEmailProperties(ativo, ativo ? "saigt@saigt-teste.invalid" : null,
                        new NotificacoesEmailProperties.Executor(1, 2, 500)),
                transactionManager, relogio);
    }

    private void linhas(Notificacao... linhas) {
        when(notificacaoRepository.findComDestinosByIdIn(anyCollection())).thenReturn(Arrays.asList(linhas));
    }

    private static NotificacaoCriadaEvento evento(boolean contactoTramitacao, Notificacao... linhas) {
        return new NotificacaoCriadaEvento(Arrays.stream(linhas).map(Notificacao::getId).toList(),
                linhas.length > 1 ? UUID.randomUUID() : null, contactoTramitacao);
    }

    private static Notificacao notificacao(TipoNotificacao tipo, String titulo, String texto) {
        Notificacao notificacao = new Notificacao(tipo, titulo, texto, null);
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        return notificacao;
    }

    private Notificacao noticiaPara(Entidade entidade) {
        Notificacao linha = notificacao(TipoNotificacao.ESTATICA, "Manutenção", "A plataforma pára no sábado.");
        ReflectionTestUtils.setField(linha, "criadoPor", autorMaster);
        linha.setEntidade(entidade);
        return linha;
    }

    private static Entidade entidade(String nome) {
        Entidade entidade = EntidadeTeste.nova("5%08d".formatted(IDS.get()), nome);
        ReflectionTestUtils.setField(entidade, "id", IDS.incrementAndGet());
        return entidade;
    }

    private static Utilizador utilizador(String nome, String email) {
        Utilizador utilizador = new Utilizador(nome, email);
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }

    private EntidadeContacto contactoPrincipal(Entidade entidade, String email) {
        EntidadeContacto contacto = new EntidadeContacto(entidade, email, autorMaster);
        contacto.setPrincipal(true);
        return contacto;
    }

    private static Processo processo(String contactoTramitacao) {
        Processo processo = new Processo(null, null, null, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setContactoTramitacao(contactoTramitacao);
        return processo;
    }

    private List<String> mensagens(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String todoOLog() {
        return String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void noticiaParaEntidadeComContactoPrincipalEnviaComOTituloEOTextoEMarcaALinha() {
        Entidade a = entidade("Câmara A");
        Notificacao linha = noticiaPara(a);
        linhas(linha);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(List.of(a.getId())))
                .thenReturn(List.of(contactoPrincipal(a, "geral@cm-a.exemplo.pt")));

        listener(true).aoNotificacaoCriada(evento(false, linha));

        verify(envioEmail).enviar("geral@cm-a.exemplo.pt", "Manutenção", "A plataforma pára no sábado.");
        assertThat(linha.isCanalEmail()).isTrue();
        assertThat(linha.getEmailEnviadoEm()).isEqualTo(AGORA);
        assertThat(mensagens(Level.INFO)).anySatisfy(m -> assertThat(m).startsWith("Cópia por email enviada")
                .contains(linha.getId().toString()).contains("enderecos=1"));
    }

    @Test
    void entidadeSemContactoPrincipalAtivoNaoRecebeMesmoComEmailDoResponsavel() {
        Entidade a = entidade("Câmara A");
        a.setResponsavelEmail("responsavel@cm-a.exemplo.pt");
        Notificacao linha = noticiaPara(a);
        linhas(linha);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList())).thenReturn(List.of());

        listener(true).aoNotificacaoCriada(evento(false, linha));

        verifyNoInteractions(envioEmail);
        assertThat(linha.isCanalEmail()).isFalse();
        assertThat(linha.getEmailEnviadoEm()).isNull();
    }

    @Test
    void respostaObrigatoriaParaUmUtilizadorVaiParaOEmailDele() {
        Utilizador u = utilizador("Ulisses Fictício", "ulisses@cm-a.exemplo.pt");
        Notificacao linha = notificacao(TipoNotificacao.RESPOSTA_OBRIGATORIA, "Esclarecimento", "Responda, por favor.");
        linha.setUtilizador(u);
        linhas(linha);

        listener(true).aoNotificacaoCriada(evento(false, linha));

        verify(envioEmail).enviar("ulisses@cm-a.exemplo.pt", "Esclarecimento", "Responda, por favor.");
        verifyNoInteractions(contactoRepository);
        assertThat(linha.isCanalEmail()).isTrue();
    }

    @Test
    void noticiaParaTodosNaoEnvia() {
        Notificacao linha = notificacao(TipoNotificacao.ESTATICA, "Para todos", "Texto.");
        linhas(linha);

        listener(true).aoNotificacaoCriada(evento(false, linha));

        verifyNoInteractions(envioEmail);
        assertThat(linha.isCanalEmail()).isFalse();
    }

    @Test
    void linhasSoComPerfilDestinoNaoEnviamNemMarcam() {
        List<Notificacao> criadas = new ArrayList<>();
        for (TipoNotificacao tipo : List.of(TipoNotificacao.PEDIDO_APOIO, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO,
                TipoNotificacao.ALERTA)) {
            Notificacao linha = notificacao(tipo, "Título", "Texto.");
            linha.setPerfilDestino(PerfilInterno.MASTER);
            criadas.add(linha);
            linhas(linha);
            listener(true).aoNotificacaoCriada(evento(false, linha));
        }

        verifyNoInteractions(envioEmail);
        assertThat(criadas).allSatisfy(linha -> {
            assertThat(linha.isCanalEmail()).isFalse();
            assertThat(linha.getEmailEnviadoEm()).isNull();
        });
    }

    @Test
    void avisoComFanOutParaTresUtilizadoresEnviaUmSoEmailAoContactoDeTramitacaoEMarcaAsTres() {
        Processo processo = processo("tramitacao@cm-a.exemplo.pt");
        List<Notificacao> fanOut = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Notificacao linha = notificacao(TipoNotificacao.TEMPORARIA, "Processo em risco de anulação",
                    "Será anulado.");
            linha.setProcesso(processo);
            linha.setUtilizador(utilizador("Pessoa " + i, "pessoa" + i + "@cm-a.exemplo.pt"));
            fanOut.add(linha);
        }
        Notificacao[] tres = fanOut.toArray(Notificacao[]::new);
        linhas(tres);

        listener(true).aoNotificacaoCriada(evento(true, tres));

        verify(envioEmail, times(1)).enviar(anyString(), anyString(), anyString());
        verify(envioEmail).enviar("tramitacao@cm-a.exemplo.pt", "Processo em risco de anulação", "Será anulado.");
        assertThat(fanOut).allSatisfy(linha -> {
            assertThat(linha.isCanalEmail()).isTrue();
            assertThat(linha.getEmailEnviadoEm()).isEqualTo(AGORA);
        });
        verifyNoInteractions(contactoRepository);
    }

    @Test
    void alertaDeProcessoSemContactoDeTramitacaoNaoEnviaEDeixaInfoComOProcessoId() {
        Processo processo = processo("  ");
        Notificacao linha = notificacao(TipoNotificacao.TEMPORARIA, "Dinâmica caducada", "Texto.");
        linha.setProcesso(processo);
        linha.setUtilizador(utilizador("Pessoa", "pessoa@cm-a.exemplo.pt"));
        linhas(linha);

        listener(true).aoNotificacaoCriada(evento(true, linha));

        verifyNoInteractions(envioEmail);
        assertThat(linha.isCanalEmail()).isFalse();
        assertThat(mensagens(Level.INFO)).anySatisfy(m -> assertThat(m)
                .startsWith("Cópia por email sem contacto de tramitação")
                .contains("processoId=" + processo.getId()));
    }

    @Test
    void duasEntidadesComOMesmoContactoPrincipalRecebemUmSoEmail() {
        Entidade a = entidade("Câmara A");
        Entidade b = entidade("Câmara B");
        Notificacao paraA = noticiaPara(a);
        Notificacao paraB = noticiaPara(b);
        linhas(paraA, paraB);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList())).thenReturn(List.of(
                contactoPrincipal(a, "Partilhado@Associacao.exemplo.pt"),
                contactoPrincipal(b, "partilhado@associacao.exemplo.pt")));

        listener(true).aoNotificacaoCriada(evento(false, paraA, paraB));

        verify(envioEmail, times(1)).enviar(anyString(), anyString(), anyString());
        assertThat(paraA.isCanalEmail()).isTrue();
        assertThat(paraB.isCanalEmail()).isTrue();
        verify(contactoRepository, times(1)).findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList());
    }

    @Test
    void falhaDoRelayNaoSobeDeixaWarnComONotificacaoIdSemEnderecoEALinhaFicaMarcada() {
        Entidade a = entidade("Câmara A");
        Notificacao linha = noticiaPara(a);
        linhas(linha);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList()))
                .thenReturn(List.of(contactoPrincipal(a, "geral@cm-a.exemplo.pt")));
        doThrow(new MailSendException("Falha ao enviar para geral@cm-a.exemplo.pt"))
                .when(envioEmail).enviar(anyString(), anyString(), anyString());
        NotificacaoCriadaEvento evento = evento(false, linha);

        assertThatCode(() -> listener(true).aoNotificacaoCriada(evento)).doesNotThrowAnyException();

        assertThat(linha.isCanalEmail()).as("a tentativa fica registada").isTrue();
        assertThat(mensagens(Level.WARN)).singleElement().satisfies(m -> assertThat(m)
                .startsWith("Falha no envio da cópia por email")
                .contains("notificacaoId=" + linha.getId())
                .contains("MailSendException")
                .doesNotContain("@"));
        assertThat(mensagens(Level.INFO)).noneMatch(m -> m.startsWith("Cópia por email enviada"));
    }

    @Test
    void falhaAoLerAsLinhasNaoSobeParaFora() {
        when(notificacaoRepository.findComDestinosByIdIn(anyCollection())).thenThrow(new IllegalStateException("x"));
        Notificacao linha = noticiaPara(entidade("Câmara A"));

        assertThatCode(() -> listener(true).aoNotificacaoCriada(evento(false, linha))).doesNotThrowAnyException();

        verifyNoInteractions(envioEmail);
        assertThat(mensagens(Level.WARN)).anySatisfy(m -> assertThat(m).startsWith("Falha na cópia por email"));
    }

    @Test
    void desligadoNaoLeNemEnviaNemMarca() {
        Notificacao linha = noticiaPara(entidade("Câmara A"));

        listener(false).aoNotificacaoCriada(evento(false, linha));

        verifyNoInteractions(notificacaoRepository, contactoRepository, envioEmail, transactionManager);
        assertThat(linha.isCanalEmail()).isFalse();
        assertThat(mensagens(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("Cópia por email desligada"));
    }

    @Test
    void asColunasSaoConfirmadasNumaTransacaoAntesDoEnvio() {
        Entidade a = entidade("Câmara A");
        Notificacao linha = noticiaPara(a);
        linhas(linha);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList()))
                .thenReturn(List.of(contactoPrincipal(a, "geral@cm-a.exemplo.pt")));
        var ordem = org.mockito.Mockito.inOrder(transactionManager, envioEmail);

        listener(true).aoNotificacaoCriada(evento(false, linha));

        ordem.verify(transactionManager).getTransaction(any());
        ordem.verify(transactionManager).commit(any());
        ordem.verify(envioEmail).enviar(eq("geral@cm-a.exemplo.pt"), anyString(), anyString());
    }

    @Test
    void oLogNuncaTemEnderecosTitulosTextosNemNomes() {
        Entidade a = entidade("Câmara A");
        Notificacao noticia = noticiaPara(a);
        Utilizador u = utilizador("Ulisses Fictício", "ulisses@cm-a.exemplo.pt");
        Notificacao resposta = notificacao(TipoNotificacao.RESPOSTA_OBRIGATORIA, "Esclarecimento", "Responda.");
        resposta.setUtilizador(u);
        linhas(noticia, resposta);
        when(contactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(anyList()))
                .thenReturn(List.of(contactoPrincipal(a, "geral@cm-a.exemplo.pt")));
        doThrow(new MailSendException("geral@cm-a.exemplo.pt recusado"))
                .when(envioEmail).enviar(eq("geral@cm-a.exemplo.pt"), anyString(), anyString());

        listener(true).aoNotificacaoCriada(evento(false, noticia, resposta));

        assertThat(todoOLog()).isNotBlank()
                .doesNotContain("@")
                .doesNotContain("Manutenção").doesNotContain("A plataforma pára")
                .doesNotContain("Esclarecimento").doesNotContain("Responda")
                .doesNotContain("Ulisses").doesNotContain("Câmara").doesNotContain("Marta");
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
        verify(envioEmail, never()).enviar(eq("marta@dgt.exemplo.pt"), anyString(), anyString());
    }
}
