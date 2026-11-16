package com.conkord.saigt.service.instrumento;

import java.util.UUID;

/** «Enviar para PCM» confirmado: o processo continua `VALIDADO_DGT`, atribuído a {@code utilizadorPcmId}. */
public record ProcessoEnviadoPcmEvento(UUID processoId, UUID utilizadorPcmId) {
}
