package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.login.LoginRecusadoEntidadeSemCorrespondencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Corre na thread do pedido de token, que termina em 400 e não tem transação: o pedido de apoio grava-se numa
 * transação própria, aberta dentro do `try` para que nem uma falha no commit mude a resposta do login.
 */
@Component
public class PedidoApoioLoginRecusadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoApoioLoginRecusadoListener.class);

    static final String TITULO = "Login recusado: entidade sem correspondência na SAIGT";
    static final String TEXTO = "O utilizador %s (%s) tentou iniciar sessão na entidade «%s» %s, que não está criada "
            + "na SAIGT. Crie ou ajuste a entidade em Gestão de Entidades.";
    static final String SEM_NIPC = "(sem NIPC devolvido pela INCM)";

    private final UtilizadorRepository utilizadorRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final ApplicationEventPublisher publicador;
    private final TransactionTemplate transacao;

    public PedidoApoioLoginRecusadoListener(UtilizadorRepository utilizadorRepository,
                                            NotificacaoRepository notificacaoRepository,
                                            ApplicationEventPublisher publicador,
                                            PlatformTransactionManager transactionManager) {
        this.utilizadorRepository = utilizadorRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.publicador = publicador;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    public void aoLoginRecusado(LoginRecusadoEntidadeSemCorrespondencia evento) {
        try {
            transacao.executeWithoutResult(estado -> criarPedidoApoio(evento));
        } catch (RuntimeException e) {
            log.error("Falha a criar o pedido de apoio de login recusado | utilizadorId={}", evento.utilizadorId(), e);
        }
    }

    private void criarPedidoApoio(LoginRecusadoEntidadeSemCorrespondencia evento) {
        Utilizador utilizador = utilizadorRepository.findById(evento.utilizadorId()).orElse(null);
        if (utilizador == null) {
            log.warn("Pedido de apoio de login recusado sem utilizador | utilizadorId={}", evento.utilizadorId());
            return;
        }
        String texto = texto(utilizador, evento);
        if (notificacaoRepository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, texto)) {
            log.info("Pedido de apoio já existente | utilizadorId={}", evento.utilizadorId());
            return;
        }
        Notificacao pedido = new Notificacao(TipoNotificacao.PEDIDO_APOIO, TITULO, texto, null);
        pedido.setPerfilDestino(PerfilInterno.MASTER);
        Notificacao gravado = notificacaoRepository.saveAndFlush(pedido);
        publicador.publishEvent(new NotificacaoCriadaEvento(List.of(gravado.getId()), null, false));
        log.info("Pedido de apoio de login recusado criado | notificacaoId={} utilizadorId={}", gravado.getId(),
                evento.utilizadorId());
    }

    static String texto(Utilizador utilizador, LoginRecusadoEntidadeSemCorrespondencia evento) {
        String nipc = evento.nipc() == null || evento.nipc().isBlank() ? SEM_NIPC : "(NIPC " + evento.nipc().strip() + ")";
        return TEXTO.formatted(utilizador.getNome(), utilizador.getEmail(), evento.descricaoIncm(), nipc);
    }
}
