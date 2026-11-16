package com.conkord.saigt.configuration;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Lido do ambiente enquanto o BackOffice não tiver onde se edite o limite. */
@Validated
@ConfigurationProperties(prefix = "app.exportacao")
public record ExportacaoProperties(@Min(1) int limiteLinhas) {
}
