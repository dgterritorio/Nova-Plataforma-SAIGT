package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Painel do Instrumento: os seus dados e as Dinâmicas por ordem cronológica, com os processos de cada Procedimento. */
public record InstrumentoPainelResponse(
        UUID id,
        Long tipoInstrumentoId,
        String designacao,
        String numero,
        String idIgtSnit,
        List<String> concelhos,
        /** Nome de cada código de `concelhos`, pela mesma ordem; omitido sem concelhos. */
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> nomesConcelhos,
        boolean emVigor,
        String origemDados,
        UUID criadoPorId,
        Instant criadoEm,
        ClassificacaoSrupInstrumento classificacaoSrup,
        InstrumentoReferenciaResponse instrumentoOrigem,
        List<DinamicaPainel> dinamicas
) {

    /** As cores finais são do design; o `semaforo` só diz qual das quatro situações se aplica. */
    public enum Semaforo {
        VERDE,
        AMARELO,
        VERMELHO,
        CINZENTO
    }

    /** Uma Dinâmica configurada e não iniciada vem sem `id`, `numeroDinamica`, `estado` nem datas, e `CINZENTO`. */
    public record DinamicaPainel(
            UUID id,
            String numeroDinamica,
            String descritivo,
            String tipoDinamica,
            String nomeDinamica,
            String estado,
            Integer tentativa,
            Semaforo semaforo,
            Instant dataInicio,
            Instant dataConclusao,
            LocalDate dataCaducidade,
            boolean historica,
            List<ProcedimentoPainel> procedimentos
    ) {
    }

    public record ProcedimentoPainel(
            Long instrumentoProcedimentoConfigId,
            String tipoProcedimento,
            String nomeProcedimento,
            int ordem,
            boolean obrigatorio,
            /** Ato pontual de «Outros atos e anexos» (FR-135): fora do sequenciamento. */
            boolean atoPontual,
            List<ProcessoPainel> processos
    ) {
    }

    public record ProcessoPainel(
            UUID id,
            String numeroProcesso,
            int ocorrencia,
            String estado,
            String numeroDr,
            LocalDate dataPublicacaoDr,
            String linkDr,
            /** `PUBLICACAO_DEPOSITO` ou `SO_DEPOSITO`. */
            String destino,
            /** Nos atos pontuais, o ato publicado que visam; nulos nos outros. */
            UUID processoReferidoId,
            String numeroProcessoReferido
    ) {
    }
}
