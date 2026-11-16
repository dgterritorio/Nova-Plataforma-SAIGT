package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** `POST /api/processos`. Os nulos não vão no JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoCriacaoProcessoPcgt(
        String codigoProcesso,
        @JsonProperty("idSSAIGT") String idSSAIGT,
        String numeroProcessoAntigo,
        String designacao,
        String tipoInstrumentoCodigo,
        String tipoProcedimentoCodigo,
        String municipioToken,
        String codigoIdus,
        EntidadePcgt entidade,
        String tipoAto,
        String numeroAto,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataAto,
        String linkAto,
        BlocoComissaoConsultivaPcgt constituicaoComissaoConsultiva,
        BlocoRatificacaoPcgt ratificacao,
        BlocoAprovacaoPcgt aprovacao,
        BlocoDepositoPcgt deposito
) {

    public PedidoCriacaoProcessoPcgt {
        exigirCodigo(codigoProcesso);
    }

    static void exigirCodigo(String codigoProcesso) {
        if (codigoProcesso == null || codigoProcesso.isBlank()) {
            throw new IllegalArgumentException("codigoProcesso é obrigatório.");
        }
    }
}
