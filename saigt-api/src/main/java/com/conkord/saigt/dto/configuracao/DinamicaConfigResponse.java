package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;

/** {@code emUso}: já existe pelo menos uma Dinâmica com esta configuração, por isso a chave não muda. */
public record DinamicaConfigResponse(Long id, TipoResumo tipoInstrumento, TipoResumo tipoDinamica,
                                     AssociacaoInstrumentos associacaoInstrumentos, boolean ativo, boolean emUso) {

    public static DinamicaConfigResponse de(InstrumentoDinamicaConfig config, boolean emUso) {
        return new DinamicaConfigResponse(config.getId(),
                new TipoResumo(config.getTipoInstrumento().getId(), config.getTipoInstrumento().getCodigo(),
                        config.getTipoInstrumento().getNome()),
                new TipoResumo(config.getTipoDinamica().getId(), config.getTipoDinamica().getCodigo(),
                        config.getTipoDinamica().getNome()),
                config.getAssociacaoInstrumentos(), config.isAtivo(), emUso);
    }
}
