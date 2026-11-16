package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlocoComissaoConsultivaPcgt(String titulo, String url, List<FicheiroReferenciaPcgt> ficheiros) {
}
