package com.conkord.saigt.service.instrumento;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.processos.inatividade")
public record InatividadeProcessoProperties(Period primeiroAviso, Period segundoAviso, Period anulacao) {

    private static final LocalDate INICIO_VERIFICACAO = LocalDate.of(2024, 1, 1);
    private static final LocalDate FIM_VERIFICACAO = LocalDate.of(2026, 1, 1);

    public InatividadeProcessoProperties {
        Objects.requireNonNull(primeiroAviso, "app.processos.inatividade.primeiro-aviso em falta");
        Objects.requireNonNull(segundoAviso, "app.processos.inatividade.segundo-aviso em falta");
        Objects.requireNonNull(anulacao, "app.processos.inatividade.anulacao em falta");
        if (!crescente(Period.ZERO, primeiroAviso) || !crescente(primeiroAviso, segundoAviso)
                || !crescente(segundoAviso, anulacao)) {
            throw new IllegalArgumentException("Os prazos de inatividade têm de ser crescentes "
                    + "(primeiro-aviso < segundo-aviso < anulacao): " + primeiroAviso + ", " + segundoAviso + ", "
                    + anulacao + ".");
        }
    }

    private static boolean crescente(Period menor, Period maior) {
        for (LocalDate inicio = INICIO_VERIFICACAO; inicio.isBefore(FIM_VERIFICACAO); inicio = inicio.plusDays(1)) {
            if (!inicio.plus(menor).isBefore(inicio.plus(maior))) {
                return false;
            }
        }
        return true;
    }
}
