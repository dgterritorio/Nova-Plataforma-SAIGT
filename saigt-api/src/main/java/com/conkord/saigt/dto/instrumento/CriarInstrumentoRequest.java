package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** `responsavelId`/`criadoPor` vêm sempre do token, nunca do corpo; o Instrumento não tem entidade. */
public record CriarInstrumentoRequest(
        @NotNull Long tipoInstrumentoId,
        @NotBlank @Size(max = 255) String designacao,
        /** IGT que já existe no SNIT: o tipo, a designação e os concelhos passam a ser os do SNIT. */
        @Pattern(regexp = "\\d{1,18}", message = "Identificador do IGT inválido.") String idIgtSnit,
        /** Obrigatório sem `idIgtSnit`, verificado no serviço; com `idIgtSnit` é ignorado. */
        @Size(max = 50) List<@NotNull @Pattern(regexp = "\\d{4}", message = "Código de concelho inválido.") String> concelhos,
        /** `TIPOLOGIA_SRUP` ativa; só nos tipos de categoria SRUP, verificado no serviço. */
        Long tipologiaSrupId,
        /** Servidão de origem ("mãe"): só nos tipos de categoria SRUP e a apontar para uma servidão, verificado no serviço. */
        UUID instrumentoOrigemId
) {

    public CriarInstrumentoRequest(Long tipoInstrumentoId, String designacao, String idIgtSnit, List<String> concelhos,
                                   Long tipologiaSrupId) {
        this(tipoInstrumentoId, designacao, idIgtSnit, concelhos, tipologiaSrupId, null);
    }

    public CriarInstrumentoRequest(Long tipoInstrumentoId, String designacao, String idIgtSnit, List<String> concelhos) {
        this(tipoInstrumentoId, designacao, idIgtSnit, concelhos, null, null);
    }

    public CriarInstrumentoRequest(Long tipoInstrumentoId, String designacao, List<String> concelhos) {
        this(tipoInstrumentoId, designacao, null, concelhos, null, null);
    }

    public CriarInstrumentoRequest(Long tipoInstrumentoId, String designacao, String idIgtSnit) {
        this(tipoInstrumentoId, designacao, idIgtSnit, null, null, null);
    }
}
