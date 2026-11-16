package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A notificação que deu origem a um registo externo: uma regularização da entidade ou uma resposta obrigatória do
 * Master. Corre na transação do registo, que a bloqueia para dois registos não a resolverem ao mesmo tempo.
 */
@Service
public class NotificacaoOrigemService {

    private static final String CAMPO_NOTIFICACAO_ORIGEM = "notificacaoOrigemId";
    private static final Set<EstadoNotificacao> ESTADOS_REGULARIZACAO =
            Set.of(EstadoNotificacao.ATIVA, EstadoNotificacao.LIDA);
    // A resposta do destinatário já resolve a resposta obrigatória; o registo pode vir depois.
    private static final Set<EstadoNotificacao> ESTADOS_RESPOSTA_OBRIGATORIA =
            Set.of(EstadoNotificacao.ATIVA, EstadoNotificacao.LIDA, EstadoNotificacao.RESOLVIDA);

    private final NotificacaoRepository notificacaoRepository;
    private final Clock relogio;

    public NotificacaoOrigemService(NotificacaoRepository notificacaoRepository, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.relogio = relogio;
    }

    public Notificacao carregarParaRegistoExterno(UUID notificacaoId) {
        Notificacao notificacao = notificacaoRepository.findComBloqueioById(notificacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação de origem não encontrada."));
        Set<EstadoNotificacao> aceites = switch (notificacao.getTipo()) {
            case REGULARIZACAO_PROCEDIMENTO -> ESTADOS_REGULARIZACAO;
            case RESPOSTA_OBRIGATORIA -> ESTADOS_RESPOSTA_OBRIGATORIA;
            default -> throw new ValidacaoException("Payload inválido.", List.of(new ValidacaoException.CampoErro(
                    CAMPO_NOTIFICACAO_ORIGEM, "A notificação de origem não é uma regularização nem uma resposta obrigatória.")));
        };
        if (!aceites.contains(notificacao.getEstado())) {
            throw new EstadoInvalidoException("A notificação de origem já foi resolvida.");
        }
        return notificacao;
    }

    /**
     * Uma resposta obrigatória já resolvida mantém o estado e a data; já ligada a outro processo, mantém-no e a
     * ligação ao processo novo fica só na auditoria do registo.
     */
    public void resolver(Notificacao notificacao, Processo processo) {
        if (notificacao.getEstado() != EstadoNotificacao.RESOLVIDA) {
            notificacao.resolver(Instant.now(relogio));
        }
        if (notificacao.getProcesso() == null) {
            notificacao.setProcesso(processo);
        }
    }
}
