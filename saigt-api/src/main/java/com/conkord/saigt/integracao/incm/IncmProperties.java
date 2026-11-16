package com.conkord.saigt.integracao.incm;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.incm")
public record IncmProperties(
        String baseUrl,
        String serviceKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @Override
    public String toString() {
        return "IncmProperties[baseUrl=" + baseUrl + ", serviceKey=***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
