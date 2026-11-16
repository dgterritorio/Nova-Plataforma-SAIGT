package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/** Item da pesquisa e detalhe do IGT: só os campos que a SAIGT usa. `municipalities` só vem no detalhe. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IgtSnit(Long idIgt, String typeCode, String designation, String validityStatus,
                      List<MunicipioIgtSnit> municipalities) {

    public static final String EM_VIGOR = "in_force";

    public IgtSnit {
        municipalities = municipalities == null ? List.of() : municipalities.stream().filter(Objects::nonNull).toList();
    }

    public IgtSnit(Long idIgt, String typeCode, String designation, String validityStatus) {
        this(idIgt, typeCode, designation, validityStatus, List.of());
    }

    public boolean emVigor() {
        return EM_VIGOR.equals(validityStatus);
    }
}
