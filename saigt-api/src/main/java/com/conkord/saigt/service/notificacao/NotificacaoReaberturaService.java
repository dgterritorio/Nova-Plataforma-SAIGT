package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoTipoNaoReabrivelException;
import com.conkord.saigt.persistence.operacional.ContagemPorNotificacao;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificacaoReaberturaService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoReaberturaService.class);

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMensagemRepository mensagemRepository;
    private final NotificacaoDocumentoRepository documentoRepository;
    private final NotificacaoVisibilidade visibilidade;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final Clock relogio;

    public NotificacaoReaberturaService(NotificacaoRepository notificacaoRepository,
                                        NotificacaoMensagemRepository mensagemRepository,
                                        NotificacaoDocumentoRepository documentoRepository,
                                        NotificacaoVisibilidade visibilidade,
                                        ContextoUtilizadorResolver contextoUtilizadorResolver, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.mensagemRepository = mensagemRepository;
        this.documentoRepository = documentoRepository;
        this.visibilidade = visibilidade;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.relogio = relogio;
    }

    /** Com a linha bloqueada: de dois pedidos simultâneos, o segundo já a encontra ativa e recebe 409. */
    @Transactional
    public NotificacaoResumoResponse reabrir(UUID id) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        // TECNICO e VISITANTE não acedem ao Centro de Notificações: não distinguem a linha que não veem.
        if (contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new NotificacaoNaoEncontradaException();
        }
        Notificacao notificacao = notificacaoRepository.findComBloqueioById(id)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        if (!notificacaoRepository.exists(visivelComId(id, contexto))) {
            throw new NotificacaoNaoEncontradaException();
        }
        if (notificacao.getTipo() != TipoNotificacao.PEDIDO_APOIO) {
            throw new NotificacaoTipoNaoReabrivelException();
        }
        if (!autor(notificacao, contexto)) {
            throw new NotificacaoPerfilNaoAutorizadoException("Só quem criou o pedido o pode reabrir.");
        }
        if (notificacao.getEstado() != EstadoNotificacao.RESOLVIDA) {
            throw new NotificacaoEstadoInvalidoException("Só um pedido de apoio resolvido pode ser reaberto.");
        }
        notificacao.reabrir();

        log.info("Pedido de apoio reaberto: notificacaoId={}, utilizadorId={}", id, contexto.utilizadorId());
        return NotificacaoResumoResponse.de(notificacao, total(mensagemRepository.contarPorNotificacao(List.of(id))),
                total(documentoRepository.contarPorNotificacao(List.of(id))));
    }

    /** A mesma regra de {@link #reabrir}, sem gravar, para o detalhe dizer se o chamador pode reabrir. */
    public static boolean podeReabrir(Notificacao notificacao, ContextoUtilizador contexto) {
        return notificacao.getTipo() == TipoNotificacao.PEDIDO_APOIO
                && notificacao.getEstado() == EstadoNotificacao.RESOLVIDA && autor(notificacao, contexto);
    }

    private static boolean autor(Notificacao notificacao, ContextoUtilizador contexto) {
        return notificacao.getCriadoPor() != null
                && Objects.equals(notificacao.getCriadoPor().getId(), contexto.utilizadorId());
    }

    private Specification<Notificacao> visivelComId(UUID id, ContextoUtilizador contexto) {
        return Specification.<Notificacao>where((root, query, cb) -> cb.equal(root.get("id"), id))
                .and(visibilidade.visiveisPara(contexto))
                .and(visibilidade.emVigencia(Instant.now(relogio)));
    }

    private static long total(List<ContagemPorNotificacao> contagens) {
        return contagens.stream().mapToLong(ContagemPorNotificacao::getTotal).sum();
    }
}
