package com.conkord.saigt.service.geoconform;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Depois do commit da passagem a `PUBLICADO`: uma falha aqui fica no log e nunca reverte a publicação. */
@Slf4j
@Component
public class CriacaoProcessoGeoconformListener {

    private final CriacaoProcessoGeoconformService servico;

    public CriacaoProcessoGeoconformListener(CriacaoProcessoGeoconformService servico) {
        this.servico = servico;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoTransitar(ProcessoTransitouEvento evento) {
        if (evento.destino() != EstadoProcesso.PUBLICADO) {
            return;
        }
        try {
            servico.criarSeAplicavel(evento.processoId());
        } catch (RuntimeException e) {
            log.error("Criação do processo no GeoConform falhou — processoId={} erro={}", evento.processoId(),
                    e.getClass().getSimpleName());
        }
    }
}
