package com.conkord.saigt.service.relatorio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Médias e taxas às unidades, com o meio a arredondar para cima, enquanto o `valor` dos KPIs for inteiro. */
final class ArredondamentoKpi {

    private ArredondamentoKpi() {
    }

    static Long unidades(BigDecimal valor) {
        return valor == null ? null : valor.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Nula com o todo a zero: sem base, não há taxa. */
    static Long percentagem(long parte, long todo) {
        return todo == 0 ? null : BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(todo), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
