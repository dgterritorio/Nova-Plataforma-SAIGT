package com.conkord.saigt.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Period;
import java.util.List;

/**
 * `chaves` tem o formato `id:segredo`; a `chaveAtiva` assina e todas validam, para a rotação não invalidar os
 * tickets em curso. Sem default: o arranque falha se faltar a chave.
 */
@Validated
@ConfigurationProperties(prefix = "app.documentos.descarga")
public record DescargaProperties(
        @NotEmpty List<String> chaves,
        @NotBlank String chaveAtiva,
        @DefaultValue("60s") Duration ticketValidade,
        @DefaultValue("50") @Min(1) int limiteSimultaneas,
        @DefaultValue("3") @Min(1) int limitePorToken,
        @DefaultValue("12m") Period retencaoIp,
        @DefaultValue("5s") Duration retryAfter,
        @DefaultValue("5") @Min(1) int limitePorClienteSnit
) {
}
