package com.conkord.saigt.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Credencial em falta faz falhar o arranque, com o nome da propriedade na mensagem. */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        @DefaultValue("true") boolean inicializarBucket,
        @DefaultValue("true") boolean objectLockObrigatorio,
        @DefaultValue("10s") Duration timeoutLigacao,
        @DefaultValue("60s") Duration timeoutSemDados
) {
}
