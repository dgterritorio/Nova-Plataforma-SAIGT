package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoValidacao;

import java.util.UUID;

public record ItemValidacaoResponse(
        TipoItemValidacao tipo,
        UUID itemId,
        EstadoValidacao estadoValidacao,
        RejeicaoItemResponse rejeicao
) {
}
