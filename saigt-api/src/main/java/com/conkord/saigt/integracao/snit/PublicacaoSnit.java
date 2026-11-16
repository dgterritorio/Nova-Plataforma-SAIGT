package com.conkord.saigt.integracao.snit;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Pedido de registo da publicação no SNIT (`SaigtPublicationInboundDto`); `ssaigtId` é o n.º de processo SAIGT. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicacaoSnit(
        Long ssaigtId,
        Integer typeId,
        Integer procedureId,
        Integer classId,
        String designation,
        String nut2Code,
        String municipalityDtcc,
        String summary,
        String publicationStatus,
        PublicacaoDrSnit publication,
        VerificacaoSnit verification
) {
}
