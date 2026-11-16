package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/** A PCGT só aplica os ficheiros de cada parte quando vem a data dessa parte. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlocoAprovacaoPcgt(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataDeliberacaoCmEim,
        List<FicheiroReferenciaPcgt> ficheirosCmEim,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataDeliberacaoAm,
        List<FicheiroReferenciaPcgt> ficheirosAm
) {
}
