package com.conkord.saigt.dto.instrumento;

import java.util.List;

/**
 * Primeiro passo do assistente «Criar Processo» (API-PROC-029): os tipos de Instrumento a partir dos quais a sessão
 * pode iniciar uma Dinâmica, e as Dinâmicas que se iniciam sem IGT de partida.
 */
public record TiposProcessoResponse(List<TipoComAssociacaoIgt> comAssociacaoIgt,
                                    List<TipoSemAssociacaoIgt> semAssociacaoIgt) {

    public record TipoComAssociacaoIgt(Long tipoInstrumentoId, String codigo, String nome) {
    }

    public record TipoSemAssociacaoIgt(Long tipoDinamicaId, String codigo, String nome,
                                       List<ConfiguracaoSemIgt> configuracoes) {
    }

    /** `associacaoInstrumentos`: quantos Instrumentos se associam depois (`UM`, `UM_OU_MAIS`, `OPCIONAL`). */
    public record ConfiguracaoSemIgt(Long instrumentoDinamicaConfigId, Long tipoInstrumentoId, String tipoInstrumentoCodigo,
                                     String tipoInstrumentoNome, String associacaoInstrumentos) {
    }
}
