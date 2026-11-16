package com.conkord.saigt.service.documento;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** O último depósito comunicado de um processo: o manifesto pelo qual o SNIT lê, com as suas caixas. */
public record DepositoSnitManifestado(UUID manifestoId, UUID processoId, Instant criadoEm, Set<String> caixas) {
}
