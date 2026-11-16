package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;

import java.util.List;
import java.util.UUID;

/**
 * Uma transição de `processo.estado` confirmada pelo pedido ou pelo job. Na rejeição DGT, o motivo é de cada item
 * rejeitado: o evento leva a recusa ({@code rejeicaoId}) e é dela que se leem os itens e os motivos.
 */
public record ProcessoTransitouEvento(UUID processoId, EstadoProcesso origem, EstadoProcesso destino, Long rejeicaoId,
                                      String motivo, List<String> mensagensIncm) {

    public ProcessoTransitouEvento {
        mensagensIncm = mensagensIncm == null ? List.of() : List.copyOf(mensagensIncm);
    }

    public static ProcessoTransitouEvento de(UUID processoId, EstadoProcesso origem, EstadoProcesso destino) {
        return new ProcessoTransitouEvento(processoId, origem, destino, null, null, List.of());
    }

    public static ProcessoTransitouEvento rejeicaoDgt(UUID processoId, Long rejeicaoId) {
        return new ProcessoTransitouEvento(processoId, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.REJEITADO_DGT,
                rejeicaoId, null, List.of());
    }

    public static ProcessoTransitouEvento anulacaoDgt(UUID processoId, EstadoProcesso origem, String motivo) {
        return new ProcessoTransitouEvento(processoId, origem, EstadoProcesso.ANULADO, null, motivo, List.of());
    }

    public static ProcessoTransitouEvento rejeicaoIncm(UUID processoId, EstadoProcesso origem, List<String> mensagens) {
        return new ProcessoTransitouEvento(processoId, origem, EstadoProcesso.ANULADO_INCM, null, null, mensagens);
    }
}
