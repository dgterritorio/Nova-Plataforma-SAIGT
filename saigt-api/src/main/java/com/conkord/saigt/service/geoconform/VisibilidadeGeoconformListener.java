package com.conkord.saigt.service.geoconform;

import com.conkord.saigt.service.instrumento.AlertaCaducidadeDinamicaEvento;
import com.conkord.saigt.service.instrumento.LimiarCaducidade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Depois do commit da passagem a `CADUCADA` (limiar `DATA`); os avisos de 1 mês e de 15 dias não mexem no GeoConform.
 * Uma falha aqui fica no log e nunca reverte a caducidade.
 */
@Slf4j
@Component
public class VisibilidadeGeoconformListener {

    private final VisibilidadeGeoconformService servico;

    public VisibilidadeGeoconformListener(VisibilidadeGeoconformService servico) {
        this.servico = servico;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCaducar(AlertaCaducidadeDinamicaEvento evento) {
        if (evento.limiar() != LimiarCaducidade.DATA) {
            return;
        }
        try {
            servico.retirarPorCaducidade(evento.dinamicaId(), evento.dataCaducidade());
        } catch (RuntimeException e) {
            log.error("Retirar o processo da lista de ativáveis do GeoConform falhou — dinamicaId={} erro={}",
                    evento.dinamicaId(), e.getClass().getSimpleName());
        }
    }
}
