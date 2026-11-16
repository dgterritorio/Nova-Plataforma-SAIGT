package com.conkord.saigt.integracao.smtp;

import com.conkord.saigt.configuration.NotificacoesEmailProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEnvioEmailTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEnvioEmail envio = new SmtpEnvioEmail(mailSender, new NotificacoesEmailProperties(true,
            "saigt@saigt-teste.invalid", new NotificacoesEmailProperties.Executor(1, 2, 500)));

    private MimeMessage enviada() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private void comMensagemReal() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void usaOJavaMailSenderComORemetenteConfiguradoTextoSimplesEUtf8() throws Exception {
        comMensagemReal();

        envio.enviar("geral@cm-a.saigt-teste.invalid", "Manutenção programada", "A plataforma pára no sábado.");

        MimeMessage mensagem = enviada();
        mensagem.saveChanges();
        assertThat(mensagem.getFrom()).containsExactly(new InternetAddress("saigt@saigt-teste.invalid"));
        assertThat(mensagem.getRecipients(Message.RecipientType.TO))
                .containsExactly(new InternetAddress("geral@cm-a.saigt-teste.invalid"));
        assertThat(mensagem.getSubject()).isEqualTo("Manutenção programada");
        assertThat(mensagem.getContent()).isEqualTo("A plataforma pára no sábado.");
        assertThat(mensagem.getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
        assertThat(mensagem.getHeader("Subject")[0]).as("assunto codificado em UTF-8").containsIgnoringCase("UTF-8");
    }

    @Test
    void tituloComQuebrasDeLinhaDaUmAssuntoNumaSoLinha() throws Exception {
        comMensagemReal();

        envio.enviar("geral@cm-a.saigt-teste.invalid", "Aviso\r\nBcc: intruso@saigt-teste.invalid\nfim", "Texto");

        MimeMessage mensagem = enviada();
        mensagem.saveChanges();
        assertThat(mensagem.getSubject()).isEqualTo("Aviso  Bcc: intruso@saigt-teste.invalid fim")
                .doesNotContain("\r").doesNotContain("\n");
        assertThat(mensagem.getRecipients(Message.RecipientType.BCC)).isNull();
        assertThat(SmtpEnvioEmail.assuntoNumaLinha("a\rb\nc\r\nd")).isEqualTo("a b c  d");
    }

    @Test
    void aFalhaDoRelaySobeComoMailException() {
        comMensagemReal();
        doThrow(new MailSendException("relay indisponível")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> envio.enviar("geral@cm-a.saigt-teste.invalid", "Título", "Texto"))
                .isInstanceOf(MailSendException.class);
    }
}
