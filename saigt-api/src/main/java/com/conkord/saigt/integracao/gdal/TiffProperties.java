package com.conkord.saigt.integracao.gdal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** `gdalinfo`: o executável e o tempo máximo de cada inspeção, findo o qual o processo é morto. */
@Validated
@ConfigurationProperties(prefix = "app.documentos.tiff")
public record TiffProperties(
        @NotBlank @DefaultValue("gdalinfo") String gdalinfo,
        @NotNull @DefaultValue("60s") Duration timeout) {
}
