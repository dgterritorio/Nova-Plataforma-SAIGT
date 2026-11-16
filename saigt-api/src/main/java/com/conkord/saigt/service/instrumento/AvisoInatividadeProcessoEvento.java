package com.conkord.saigt.service.instrumento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AvisoInatividadeProcessoEvento(UUID processoId, LimiarInatividade limiar, Instant momentoLimiar,
                                             Instant atualizadoEmAvaliado, LocalDate dataAnulacaoPrevista) {
}
