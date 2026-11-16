package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.integracao.core.CircuitoAbertoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Corre na thread da chamada externa que abriu o circuito: nenhuma falha do alerta pode chegar a essa chamada. */
@Component
public class AlertaIntegracaoListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaIntegracaoListener.class);

    private final AlertaIntegracaoService alertaIntegracaoService;

    public AlertaIntegracaoListener(AlertaIntegracaoService alertaIntegracaoService) {
        this.alertaIntegracaoService = alertaIntegracaoService;
    }

    @EventListener
    public void aoAbrirCircuito(CircuitoAbertoEvento evento) {
        try {
            alertaIntegracaoService.criarAlertaCircuitoAberto(evento);
        } catch (RuntimeException e) {
            log.error("Falha a criar o alerta de integração | sistema={} circuito={}", evento.sistema(),
                    evento.circuito(), e);
        }
    }
}
