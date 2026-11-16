package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;

public record RejeicaoItemResponse(
        Long categoriaId,
        Long motivoId,
        String notas
) {
    public static RejeicaoItemResponse de(ProcessoValidacaoRejeicao rejeicao) {
        return new RejeicaoItemResponse(
                rejeicao.getCategoria() == null ? null : rejeicao.getCategoria().getId(),
                rejeicao.getMotivo() == null ? null : rejeicao.getMotivo().getId(),
                rejeicao.getNotas());
    }
}
