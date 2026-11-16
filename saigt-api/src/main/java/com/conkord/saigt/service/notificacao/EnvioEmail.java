package com.conkord.saigt.service.notificacao;

/** Envio da cópia de cortesia por email; as falhas sobem como exceção não verificada. */
public interface EnvioEmail {

    void enviar(String destinatario, String assunto, String corpo);
}
