package com.conkord.saigt.integracao.smtp;

import com.conkord.saigt.configuration.NotificacoesEmailProperties;
import com.conkord.saigt.service.notificacao.EnvioEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Texto simples em UTF-8, sem templates: a composição do email está fora de âmbito. */
@Component
public class SmtpEnvioEmail implements EnvioEmail {

    private final JavaMailSender mailSender;
    private final NotificacoesEmailProperties propriedades;

    public SmtpEnvioEmail(JavaMailSender mailSender, NotificacoesEmailProperties propriedades) {
        this.mailSender = mailSender;
        this.propriedades = propriedades;
    }

    @Override
    public void enviar(String destinatario, String assunto, String corpo) {
        MimeMessage mensagem = mailSender.createMimeMessage();
        try {
            MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, false, StandardCharsets.UTF_8.name());
            ajudante.setFrom(propriedades.remetente());
            ajudante.setTo(destinatario);
            ajudante.setSubject(assuntoNumaLinha(assunto));
            ajudante.setText(corpo == null ? "" : corpo, false);
        } catch (MessagingException e) {
            throw new MailPreparationException("Mensagem de email inválida", e);
        }
        mailSender.send(mensagem);
    }

    /** Quebras de linha no assunto abririam cabeçalhos novos (injeção de cabeçalhos). */
    static String assuntoNumaLinha(String assunto) {
        return assunto == null ? "" : assunto.replace('\r', ' ').replace('\n', ' ');
    }
}
