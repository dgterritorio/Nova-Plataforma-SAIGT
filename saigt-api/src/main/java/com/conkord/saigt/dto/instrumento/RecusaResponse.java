package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.OrigemRejeicao;

import java.time.Instant;
import java.util.List;

public record RecusaResponse(
        int sequencia,
        OrigemRejeicao origem,
        int submissao,
        Instant data,
        String notasGerais,
        List<String> motivoIncm,
        List<ItemRecusaResponse> itens
) {
}
