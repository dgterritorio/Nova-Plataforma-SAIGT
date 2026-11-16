package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;

import java.util.UUID;

public record ItemRecusaResponse(
        TipoItemValidacao tipo,
        UUID documentoId,
        String nomeFicheiro,
        String separador,
        ElementoInformacaoBase elemento,
        ValorListaResponse categoria,
        ValorListaResponse motivo,
        String notas
) {
    public static ItemRecusaResponse de(ProcessoRejeicaoItem item) {
        ValorListaResponse categoria = ValorListaResponse.de(item.getCategoria());
        ValorListaResponse motivo = ValorListaResponse.de(item.getMotivo());
        ProcessoDocumento documento = item.getProcessoDocumento();
        if (documento != null) {
            String separador = documento.getDocumentoConfig() == null ? null : documento.getDocumentoConfig().getSeparador();
            return new ItemRecusaResponse(TipoItemValidacao.DOCUMENTO, documento.getId(), documento.getNomeFicheiro(),
                    separador, null, categoria, motivo, item.getNotas());
        }
        return new ItemRecusaResponse(TipoItemValidacao.INFORMACAO_BASE, null, null, null,
                item.getProcessoInformacaoBase().getElemento(), categoria, motivo, item.getNotas());
    }
}
