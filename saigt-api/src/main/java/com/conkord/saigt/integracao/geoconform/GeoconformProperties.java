package com.conkord.saigt.integracao.geoconform;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** O cliente e o segredo OAuth vêm só do ambiente e nunca aparecem no `toString`. */
@Validated
@ConfigurationProperties(prefix = "app.geoconform")
public record GeoconformProperties(
        String baseUrl,
        String tokenUrl,
        String clientId,
        String clientSecret,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    /** Sem URL base, URL do token ou credenciais, nenhuma operação sai. */
    public boolean configurado() {
        return preenchido(baseUrl) && preenchido(tokenUrl) && preenchido(clientId) && preenchido(clientSecret);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    @Override
    public String toString() {
        return "GeoconformProperties[baseUrl=" + baseUrl + ", tokenUrl=" + tokenUrl + ", clientId=***, clientSecret=***, "
                + "connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
