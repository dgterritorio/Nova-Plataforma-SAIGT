package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.integracao.snit.FicheirosRecebidosSnit;

import java.util.UUID;

/** `ficheiros` é nulo quando não houve pedido: depósito recusado ou sem ZIP a comunicar. */
public record ComunicacaoDepositoSnit(
        UUID manifestoId,
        ResultadoChamadaExterna<DepositoRecebidoSnit> deposito,
        ResultadoChamadaExterna<FicheirosRecebidosSnit> ficheiros
) {
}
