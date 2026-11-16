package com.conkord.saigt.service.instrumento;

import java.time.LocalDate;
import java.util.UUID;

/** Publicado dentro da transação de {@code CaducidadeDinamicaService#avaliar}, para listeners `AFTER_COMMIT`. */
public record AlertaCaducidadeDinamicaEvento(UUID dinamicaId, LimiarCaducidade limiar, LocalDate dataCaducidade) {
}
