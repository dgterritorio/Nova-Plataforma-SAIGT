package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.CriarMensagemNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.MensagemNotificacaoResponse;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoSemConversacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagem;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificacaoMensagemService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoMensagemService.class);

    private static final Set<TipoNotificacao> TIPOS_COM_CONVERSACAO = Set.of(TipoNotificacao.RESPOSTA_OBRIGATORIA,
            TipoNotificacao.PEDIDO_APOIO, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO);

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMensagemRepository mensagemRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final NotificacaoVisibilidade visibilidade;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final Clock relogio;

    public NotificacaoMensagemService(NotificacaoRepository notificacaoRepository,
                                      NotificacaoMensagemRepository mensagemRepository,
                                      UtilizadorRepository utilizadorRepository, NotificacaoVisibilidade visibilidade,
                                      ContextoUtilizadorResolver contextoUtilizadorResolver, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.mensagemRepository = mensagemRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.visibilidade = visibilidade;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.relogio = relogio;
    }

    @Transactional
    public CriarMensagemNotificacaoResponse responder(UUID id, String texto) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        // TECNICO e VISITANTE não acedem ao Centro de Notificações: recebem a resposta de uma linha que não veem.
        if (internoSemAcesso(contexto)) {
            throw new NotificacaoNaoEncontradaException();
        }
        Notificacao notificacao = notificacaoRepository.findComBloqueioById(id)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        if (!notificacaoRepository.exists(visivelComId(id, contexto))) {
            throw new NotificacaoNaoEncontradaException();
        }
        if (!temConversacao(notificacao)) {
            throw new NotificacaoSemConversacaoException();
        }
        Participacao participacao = participacao(notificacao, contexto);
        if (!participacao.participa()) {
            throw new NotificacaoPerfilNaoAutorizadoException("Não pode responder a esta notificação.");
        }
        if (!aceitaMensagens(notificacao)) {
            throw new NotificacaoEstadoInvalidoException();
        }

        Utilizador autor = utilizadorRepository.getReferenceById(contexto.utilizadorId());
        NotificacaoMensagem mensagem = mensagemRepository.saveAndFlush(
                new NotificacaoMensagem(notificacao, autor, texto.strip()));
        if (resolvePelaMensagem(notificacao, participacao)) {
            notificacao.resolver(Instant.now(relogio));
        }

        log.info("Mensagem de notificação registada: notificacaoId={}, tipo={}, estado={}, utilizadorId={}",
                notificacao.getId(), notificacao.getTipo(), notificacao.getEstado(), contexto.utilizadorId());
        return new CriarMensagemNotificacaoResponse(MensagemNotificacaoResponse.de(mensagem), notificacao.getEstado());
    }

    /** A mesma regra de {@link #responder}, sem gravar, para uma linha que o chamador já vê. */
    public boolean podeResponder(Notificacao notificacao, ContextoUtilizador contexto) {
        return !internoSemAcesso(contexto) && temConversacao(notificacao)
                && participacao(notificacao, contexto).participa() && aceitaMensagens(notificacao);
    }

    boolean participa(Notificacao notificacao, ContextoUtilizador contexto) {
        return participacao(notificacao, contexto).participa();
    }

    private Specification<Notificacao> visivelComId(UUID id, ContextoUtilizador contexto) {
        return Specification.<Notificacao>where((root, query, cb) -> cb.equal(root.get("id"), id))
                .and(visibilidade.visiveisPara(contexto))
                .and(visibilidade.emVigencia(Instant.now(relogio)));
    }

    private static boolean internoSemAcesso(ContextoUtilizador contexto) {
        return contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER;
    }

    private static boolean temConversacao(Notificacao notificacao) {
        return TIPOS_COM_CONVERSACAO.contains(notificacao.getTipo());
    }

    /** A conversa da Resposta obrigatória continua depois de resolvida; nas outras, só enquanto está ativa. */
    static boolean aceitaMensagens(Notificacao notificacao) {
        return notificacao.getEstado() == EstadoNotificacao.ATIVA
                || (notificacao.getEstado() == EstadoNotificacao.RESOLVIDA
                && notificacao.getTipo() == TipoNotificacao.RESPOSTA_OBRIGATORIA);
    }

    private static boolean resolvePelaMensagem(Notificacao notificacao, Participacao participacao) {
        if (notificacao.getEstado() != EstadoNotificacao.ATIVA || participacao.autor()) {
            return false;
        }
        return switch (notificacao.getTipo()) {
            case PEDIDO_APOIO -> participacao.masterDestino();
            case RESPOSTA_OBRIGATORIA -> participacao.destinatario();
            default -> false;
        };
    }

    private static Participacao participacao(Notificacao notificacao, ContextoUtilizador contexto) {
        UUID utilizadorId = contexto.utilizadorId();
        boolean autor = notificacao.getCriadoPor() != null
                && Objects.equals(notificacao.getCriadoPor().getId(), utilizadorId);
        boolean destinatarioUtilizador = notificacao.getUtilizador() != null
                && Objects.equals(notificacao.getUtilizador().getId(), utilizadorId);
        boolean destinatarioEntidade = contexto.perfilInterno() == null && contexto.entidadeId() != null
                && notificacao.getEntidade() != null
                && Objects.equals(notificacao.getEntidade().getId(), contexto.entidadeId());
        boolean masterDestino = contexto.perfilInterno() == PerfilInterno.MASTER
                && notificacao.getPerfilDestino() == PerfilInterno.MASTER;
        return new Participacao(autor, destinatarioUtilizador || destinatarioEntidade, masterDestino);
    }

    private record Participacao(boolean autor, boolean destinatario, boolean masterDestino) {

        boolean participa() {
            return autor || destinatario || masterDestino;
        }
    }
}
