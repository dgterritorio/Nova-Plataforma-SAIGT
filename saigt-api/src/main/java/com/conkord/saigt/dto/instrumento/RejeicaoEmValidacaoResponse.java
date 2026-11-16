package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;

import java.util.UUID;

public record RejeicaoEmValidacaoResponse(
        TipoItemValidacao tipo,
        UUID itemId,
        Long categoriaId,
        Long motivoId,
        String notas
) {
    public static RejeicaoEmValidacaoResponse de(ProcessoValidacaoRejeicao rejeicao) {
        RejeicaoItemResponse campos = RejeicaoItemResponse.de(rejeicao);
        return rejeicao.getProcessoDocumento() != null
                ? new RejeicaoEmValidacaoResponse(TipoItemValidacao.DOCUMENTO, rejeicao.getProcessoDocumento().getId(),
                        campos.categoriaId(), campos.motivoId(), campos.notas())
                : new RejeicaoEmValidacaoResponse(TipoItemValidacao.INFORMACAO_BASE,
                        rejeicao.getProcessoInformacaoBase().getId(), campos.categoriaId(), campos.motivoId(),
                        campos.notas());
    }
}
