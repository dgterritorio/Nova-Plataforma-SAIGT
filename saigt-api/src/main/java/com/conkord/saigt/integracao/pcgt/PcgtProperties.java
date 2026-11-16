package com.conkord.saigt.integracao.pcgt;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.pcgt")
public record PcgtProperties(
        String baseUrl,
        String username,
        String password,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @Override
    public String toString() {
        return "PcgtProperties[baseUrl=" + baseUrl + ", username=***, password=***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
