package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.SituacaoPcm;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Construído dentro da transação de leitura: navega associações LAZY (open-in-view desligado).
 * `designacaoInstrumento` é só apresentação: a do Instrumento de início ou, numa Dinâmica iniciada sem IGT, o
 * `descritivo` dela; navega-se por `instrumentoId`, nulo nesse caso.
 * `situacaoPcm` e `pcmDevolvidoEm` são só para os perfis internos; nulos para as entidades e para a PCM.
 */
public record ProcessoListagemResponse(
        UUID id,
        String numeroProcesso,
        UUID instrumentoId,
        String designacaoInstrumento,
        String numeroInstrumento,
        String tipoInstrumento,
        String tipoDinamica,
        String nomeDinamica,
        String tipoProcedimento,
        String nomeProcedimento,
        Long entidadeId,
        String entidadeCodigo,
        String entidadeNome,
        String estado,
        Instant atualizadoEm,
        String autorNome,
        List<InstrumentoAbrangidoResponse> instrumentosAbrangidos,
        SituacaoPcm situacaoPcm,
        Instant pcmDevolvidoEm,
        /** Nomes dos concelhos do Instrumento de início, pela ordem dos códigos DTCC; vazio sem Instrumento. */
        List<String> concelhos
) {
    /**
     * `nomesConcelhos` é o mapa da página inteira, por id do Instrumento, lido de uma vez
     * ({@code TerritorioInstrumentoService#nomesConcelhos}).
     */
    public static ProcessoListagemResponse de(Processo processo, boolean perfilInterno,
                                              Map<UUID, List<String>> nomesConcelhos) {
        Dinamica dinamica = processo.getDinamica();
        Instrumento instrumento = dinamica.instrumentoDeInicio().orElse(null);
        return new ProcessoListagemResponse(
                processo.getId(),
                processo.getNumeroProcesso(),
                instrumento == null ? null : instrumento.getId(),
                instrumento == null ? dinamica.getDescritivo() : instrumento.getDesignacao(),
                instrumento == null ? null : instrumento.getNumero(),
                dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(),
                processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo(),
                processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getNome(),
                processo.getEntidade().getId(),
                processo.getEntidade().getCodigo(),
                processo.getEntidade().getDescricaoIncm(),
                processo.getEstado().name(),
                processo.getAtualizadoEm(),
                processo.getCriadoPor().getNome(),
                InstrumentoAbrangidoResponse.de(dinamica),
                perfilInterno ? processo.situacaoPcm() : null,
                perfilInterno ? processo.getPcmDevolvidoEm() : null,
                instrumento == null ? List.of() : nomesConcelhos.getOrDefault(instrumento.getId(), List.of()));
    }
}
