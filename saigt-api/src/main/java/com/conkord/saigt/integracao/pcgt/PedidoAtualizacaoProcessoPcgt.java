package com.conkord.saigt.integracao.pcgt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * `PUT /api/processos`: substitui os campos simples na PCGT (um nulo fica vazio), por isso vai sempre completo.
 * Não tem os campos que a PCGT ignora no `PUT` (designação, tipos, entidade, estado).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoAtualizacaoProcessoPcgt(
        String codigoProcesso,
        String codigoIdus,
        String municipioToken,
        String tipoAto,
        String numeroAto,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dataAto,
        String linkAto,
        BlocoComissaoConsultivaPcgt constituicaoComissaoConsultiva,
        BlocoRatificacaoPcgt ratificacao,
        BlocoAprovacaoPcgt aprovacao,
        BlocoDepositoPcgt deposito
) {

    public PedidoAtualizacaoProcessoPcgt {
        PedidoCriacaoProcessoPcgt.exigirCodigo(codigoProcesso);
    }
}
