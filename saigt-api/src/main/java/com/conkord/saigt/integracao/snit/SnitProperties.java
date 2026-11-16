package com.conkord.saigt.integracao.snit;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.snit")
public record SnitProperties(
        String baseUrl,
        String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @Override
    public String toString() {
        return "SnitProperties[baseUrl=" + baseUrl + ", apiKey=***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
