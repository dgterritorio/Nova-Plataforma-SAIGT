package com.conkord.saigt.service.snit;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/** Caixas que entram na pasta de depósito servida ao SNIT, lidas no momento de cada depósito. */
@Validated
@ConfigurationProperties(prefix = "app.snit.deposito")
public record DepositoSnitProperties(
        @DefaultValue("DEPOSITO") @NotEmpty Set<CategoriaDocumento> caixas
) {
}
