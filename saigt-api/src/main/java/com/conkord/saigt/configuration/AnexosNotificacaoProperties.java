package com.conkord.saigt.configuration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

/**
 * Os anexos de notificação não têm item documental: o limite e as extensões que nos ficheiros de processo vêm do
 * `documento_config` vêm daqui.
 */
@Validated
@ConfigurationProperties(prefix = "app.notificacoes.anexos")
public record AnexosNotificacaoProperties(@NotNull DataSize tamanhoMaximo, @NotEmpty List<String> extensoesAceites) {

    public AnexosNotificacaoProperties {
        extensoesAceites = extensoesAceites == null ? List.of() : extensoesAceites.stream()
                .map(extensao -> extensao.trim().toLowerCase(Locale.ROOT))
                .filter(extensao -> !extensao.isEmpty())
                .toList();
    }
}
