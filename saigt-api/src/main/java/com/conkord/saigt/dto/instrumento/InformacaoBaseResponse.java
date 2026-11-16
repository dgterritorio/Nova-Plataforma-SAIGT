package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;

import java.util.UUID;

public record InformacaoBaseResponse(
        UUID id,
        ElementoInformacaoBase elemento,
        EstadoValidacao estadoValidacao
) {
}
