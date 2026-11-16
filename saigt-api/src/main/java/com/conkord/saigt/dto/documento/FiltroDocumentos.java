package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;

/** Filtros opcionais da listagem de ficheiros do Processo; nulo = sem filtro. */
public record FiltroDocumentos(
        CategoriaDocumento categoria,
        Long documentoConfigId,
        EstadoValidacao estadoValidacao
) {
    public static final FiltroDocumentos SEM_FILTRO = new FiltroDocumentos(null, null, null);
}
