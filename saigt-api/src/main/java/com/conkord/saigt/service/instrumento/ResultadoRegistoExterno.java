package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.Processo;

public record ResultadoRegistoExterno(Processo processo, int documentosCopiados) {
}
