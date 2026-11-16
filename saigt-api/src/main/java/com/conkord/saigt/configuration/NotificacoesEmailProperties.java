package com.conkord.saigt.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Cópia de cortesia das notificações por email. Desligada por omissão; ligada, o arranque falha sem `remetente`.
 * O relay em si é o `spring.mail.*`.
 */
@Validated
@ConfigurationProperties(prefix = "app.notificacoes.email")
public record NotificacoesEmailProperties(
        @DefaultValue("false") boolean ativo,
        @Email String remetente,
        @Valid @DefaultValue Executor executor
) {

    static final String REMETENTE_OBRIGATORIO =
            "app.notificacoes.email.remetente é obrigatório quando app.notificacoes.email.ativo=true";

    public NotificacoesEmailProperties {
        if (ativo && (remetente == null || remetente.isBlank())) {
            throw new IllegalArgumentException(REMETENTE_OBRIGATORIO);
        }
    }

    public record Executor(
            @DefaultValue("1") @Min(1) int tamanhoBase,
            @DefaultValue("2") @Min(1) int tamanhoMaximo,
            @DefaultValue("500") @Min(1) int fila
    ) {
    }
}
