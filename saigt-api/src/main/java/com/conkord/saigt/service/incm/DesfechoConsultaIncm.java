package com.conkord.saigt.service.incm;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;

/** Resultado de uma consulta à INCM; `transicaoDerivada` é o estado para onde a resposta manda o Processo, se algum. */
public enum DesfechoConsultaIncm {
    PUBLICADO(EstadoProcesso.PUBLICADO),
    ANULADO_INCM(EstadoProcesso.ANULADO_INCM),
    SEM_ALTERACAO(null),
    FALHA_TECNICA(null);

    private final EstadoProcesso transicaoDerivada;

    DesfechoConsultaIncm(EstadoProcesso transicaoDerivada) {
        this.transicaoDerivada = transicaoDerivada;
    }

    public EstadoProcesso transicaoDerivada() {
        return transicaoDerivada;
    }
}
