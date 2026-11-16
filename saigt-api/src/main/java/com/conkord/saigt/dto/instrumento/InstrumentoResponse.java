package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Instrumento;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstrumentoResponse(
        UUID id,
        Long tipoInstrumentoId,
        String tipoInstrumentoNome,
        String numero,
        String designacao,
        UUID responsavelId,
        boolean emVigor,
        String origemDados,
        UUID criadoPorId,
        Instant criadoEm,
        /** Códigos DTCC, por ordem; na listagem e na resposta do `POST`. */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> concelhos,
        /** Nome de cada código de `concelhos`, pela mesma ordem; omitido sem concelhos. */
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> nomesConcelhos,
        ClassificacaoSrupInstrumento classificacaoSrup,
        InstrumentoReferenciaResponse instrumentoOrigem
) {
    public static InstrumentoResponse de(Instrumento instrumento) {
        return de(instrumento, null, null);
    }

    public static InstrumentoResponse de(Instrumento instrumento, List<String> concelhos, List<String> nomesConcelhos) {
        return new InstrumentoResponse(
                instrumento.getId(),
                instrumento.getTipoInstrumento().getId(),
                instrumento.getTipoInstrumento().getNome(),
                instrumento.getNumero(),
                instrumento.getDesignacao(),
                instrumento.getResponsavel().getId(),
                instrumento.isEmVigor(),
                instrumento.getOrigemDados(),
                instrumento.getCriadoPor().getId(),
                instrumento.getCriadoEm(),
                concelhos,
                nomesConcelhos,
                ClassificacaoSrupInstrumento.de(instrumento),
                InstrumentoReferenciaResponse.de(instrumento.getInstrumentoOrigem()));
    }
}
