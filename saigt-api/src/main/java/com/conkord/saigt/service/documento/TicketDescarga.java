package com.conkord.saigt.service.documento;

import java.time.Instant;
import java.util.UUID;

public record TicketDescarga(TipoTicketDescarga tipo, UUID utilizadorId, UUID documentoId, Instant expiraEm) {
}
