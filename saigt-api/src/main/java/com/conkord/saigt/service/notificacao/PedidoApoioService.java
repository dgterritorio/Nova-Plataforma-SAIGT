package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O pedido de apoio da entidade que uma transferência de autoria resolve (TR-151, BL-IAM-007 passo 5).
 * Corre dentro da transação de quem o chama: o bloqueio da linha dura até ao fim dessa transação, como na resposta
 * por mensagem, e um erro posterior desfaz também a resolução.
 */
@Service
public class PedidoApoioService {

    static final String CAMPO_PEDIDO_APOIO_ID = "pedidoApoioId";
    static final String MSG_NAO_ENCONTRADO = "Pedido de apoio não encontrado.";
    static final String MSG_NAO_PENDENTE = "O pedido de apoio indicado não está pendente.";
    static final String MSG_OUTRO_PROCESSO = "O pedido de apoio é de outro processo.";

    private final NotificacaoRepository notificacaoRepository;
    private final Clock relogio;

    public PedidoApoioService(NotificacaoRepository notificacaoRepository, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.relogio = relogio;
    }

    /**
     * Um pedido de outro tipo, já fechado ou que não se dirige aos Master não está pendente; um pedido geral (sem
     * processo) é aceite para qualquer processo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notificacao resolverPedidoApoio(Processo processo, UUID pedidoApoioId) {
        Notificacao pedido = notificacaoRepository.findComBloqueioById(pedidoApoioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADO));
        if (pedido.getTipo() != TipoNotificacao.PEDIDO_APOIO || pedido.getPerfilDestino() != PerfilInterno.MASTER
                || pedido.getEstado() != EstadoNotificacao.ATIVA) {
            throw invalido(MSG_NAO_PENDENTE);
        }
        if (pedido.getProcesso() != null && !pedido.getProcesso().getId().equals(processo.getId())) {
            throw invalido(MSG_OUTRO_PROCESSO);
        }
        pedido.resolver(Instant.now(relogio));
        return pedido;
    }

    private static ValidacaoException invalido(String mensagem) {
        return new ValidacaoException(mensagem, List.of(new ValidacaoException.CampoErro(CAMPO_PEDIDO_APOIO_ID, mensagem)));
    }
}
