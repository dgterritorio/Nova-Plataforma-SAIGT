package com.conkord.saigt.integracao.core;

import java.util.UUID;

public record ReferenciaChamada(UUID processoId, String idus, Long actIdentifier) {

    public static final ReferenciaChamada NENHUMA = new ReferenciaChamada(null, null, null);
}
