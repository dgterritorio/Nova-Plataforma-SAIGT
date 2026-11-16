package com.conkord.saigt.integracao.core;

import java.time.Instant;

/** Publicado só na passagem `CLOSED -> OPEN`; sem dados de processo nem de utilizador. */
public record CircuitoAbertoEvento(SistemaExterno sistema, String circuito, Instant momento) {
}
