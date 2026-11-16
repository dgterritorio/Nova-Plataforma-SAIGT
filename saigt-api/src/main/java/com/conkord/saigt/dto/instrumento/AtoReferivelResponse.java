package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Processo;

import java.time.LocalDate;
import java.util.UUID;

/** Um ato publicado que um ato pontual pode visar (`processoReferidoId` do `POST /dinamicas/{id}/processos`). */
public record AtoReferivelResponse(
        UUID processoId,
        String numeroProcesso,
        String tipoProcedimento,
        String numeroDr,
        LocalDate dataPublicacaoDr
) {
    public static AtoReferivelResponse de(Processo processo) {
        return new AtoReferivelResponse(processo.getId(), processo.getNumeroProcesso(),
                processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo(), processo.getNumeroDr(),
                processo.getDataPublicacaoDr());
    }
}
