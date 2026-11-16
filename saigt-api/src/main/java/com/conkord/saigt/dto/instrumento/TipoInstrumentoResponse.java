package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.TipoInstrumento;

/**
 * Com identificador e legível por qualquer sessão, ao contrário de {@code CatalogoResponse},
 * que só devolve nomes e é restrito a MASTER.
 */
public record TipoInstrumentoResponse(Long id, String codigo, String nome, AmbitoTerritorial ambitoTerritorial,
                                      String categoria, boolean entidadeInstrutoraObrigatoria) {

    public static TipoInstrumentoResponse de(TipoInstrumento tipoInstrumento) {
        return new TipoInstrumentoResponse(tipoInstrumento.getId(), tipoInstrumento.getCodigo(), tipoInstrumento.getNome(),
                tipoInstrumento.getAmbitoTerritorial(), tipoInstrumento.getCategoria(),
                tipoInstrumento.isEntidadeInstrutoraObrigatoria());
    }
}
