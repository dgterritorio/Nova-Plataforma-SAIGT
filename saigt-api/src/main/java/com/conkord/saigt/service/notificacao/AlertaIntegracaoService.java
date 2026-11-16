package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.integracao.core.CircuitoAbertoEvento;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Alerta geral aos Master, só na plataforma: uma linha com `perfil_destino` não tem endereço de email, por isso não
 * se publica `NotificacaoCriadaEvento`.
 */
@Service
public class AlertaIntegracaoService {

    private static final Logger log = LoggerFactory.getLogger(AlertaIntegracaoService.class);

    static final String TITULO = "Integração indisponível: %s";
    static final String TEXTO = "A comunicação da SAIGT com %s foi suspensa a %s depois de falhas sucessivas "
            + "(circuito %s). As chamadas voltam a ser tentadas automaticamente.";
    private static final DateTimeFormatter MOMENTO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Lisbon"));

    private final NotificacaoRepository notificacaoRepository;

    public AlertaIntegracaoService(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    /** Transação própria: o alerta fica gravado mesmo que a transação de quem fez a chamada externa reverta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void criarAlertaCircuitoAberto(CircuitoAbertoEvento evento) {
        String titulo = titulo(evento.sistema());
        // A deduplicação é pelo título, que só depende do sistema; cobre também as outras instâncias da API.
        if (notificacaoRepository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTitulo(
                TipoNotificacao.ALERTA, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, titulo)) {
            log.info("Alerta de integração já ativo | sistema={} circuito={}", evento.sistema(), evento.circuito());
            return;
        }
        String texto = TEXTO.formatted(evento.sistema().nomeApresentacao(), MOMENTO.format(evento.momento()),
                evento.circuito());
        Notificacao alerta = new Notificacao(TipoNotificacao.ALERTA, titulo, texto, null);
        alerta.setPerfilDestino(PerfilInterno.MASTER);
        alerta.setCanalEmail(false);
        Notificacao gravado = notificacaoRepository.saveAndFlush(alerta);
        log.info("Alerta de integração criado | sistema={} circuito={} notificacaoId={}", evento.sistema(),
                evento.circuito(), gravado.getId());
    }

    static String titulo(SistemaExterno sistema) {
        return TITULO.formatted(sistema.nomeApresentacao());
    }
}
