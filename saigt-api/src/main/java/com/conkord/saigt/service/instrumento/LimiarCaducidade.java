package com.conkord.saigt.service.instrumento;

import java.time.LocalDate;

/** Pela ordem em que são atingidos; o nome é o valor gravado em `dinamica.alertas_enviados`. */
public enum LimiarCaducidade {

    UM_MES {
        @Override
        boolean atingido(LocalDate dataCaducidade, LocalDate hoje) {
            return !hoje.isBefore(dataCaducidade.minusMonths(1));
        }
    },
    QUINZE_DIAS {
        @Override
        boolean atingido(LocalDate dataCaducidade, LocalDate hoje) {
            return !hoje.isBefore(dataCaducidade.minusDays(15));
        }
    },
    DATA {
        @Override
        boolean atingido(LocalDate dataCaducidade, LocalDate hoje) {
            return !hoje.isBefore(dataCaducidade);
        }
    };

    abstract boolean atingido(LocalDate dataCaducidade, LocalDate hoje);
}
