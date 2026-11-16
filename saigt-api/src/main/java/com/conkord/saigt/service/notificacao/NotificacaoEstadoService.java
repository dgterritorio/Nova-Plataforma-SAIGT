package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.dto.notificacao.RemocaoNotificacaoResponse;
import com.conkord.saigt.exception.ConkordException;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoTipoNaoRemovivelException;
import com.conkord.saigt.exception.NotificacaoTipoSemLeituraException;
import com.conkord.saigt.exception.NotificacaoTipoSemResolucaoManualException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificacaoEstadoService {

    public static final String ACAO_LER = "LER";
    public static final String ACAO_RESOLVER = "RESOLVER";
    public static final String ACAO_REMOVER = "REMOVER";

    private static final Logger log = LoggerFactory.getLogger(NotificacaoEstadoService.class);

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMensagemRepository mensagemRepository;
    private final NotificacaoDocumentoRepository documentoRepository;
    private final NotificacaoVisibilidade visibilidade;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final Clock relogio;

    public NotificacaoEstadoService(NotificacaoRepository notificacaoRepository,
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

    @Transactional
    public NotificacaoResumoResponse ler(UUID id) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Notificacao notificacao = visivel(id, contexto);
        recusar(recusaLeitura(notificacao, contexto));
        exigirAtiva(notificacao);

        int linhas = notificacaoRepository.marcarLida(id, Instant.now(relogio));
        registar(notificacao, ACAO_LER, linhas, contexto);
        return resumo(id);
    }

    @Transactional
    public NotificacaoResumoResponse resolver(UUID id) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Notificacao notificacao = visivel(id, contexto);
        recusar(recusaResolucao(notificacao, contexto));
        exigirAtiva(notificacao);

        int linhas = notificacaoRepository.marcarResolvida(id, Instant.now(relogio));
        registar(notificacao, ACAO_RESOLVER, linhas, contexto);
        return resumo(id);
    }

    @Transactional
    public RemocaoNotificacaoResponse remover(UUID id) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Notificacao notificacao = visivel(id, contexto);
        recusar(recusaRemocao(notificacao, contexto));
        exigirAtiva(notificacao);

        Instant agora = Instant.now(relogio);
        int linhas = notificacao.getTipo() == TipoNotificacao.ESTATICA && notificacao.getLoteId() != null
                ? notificacaoRepository.marcarRemovidasDoLote(notificacao.getLoteId(), agora)
                : notificacaoRepository.marcarRemovida(id, agora);
        registar(notificacao, ACAO_REMOVER, linhas, contexto);
        return new RemocaoNotificacaoResponse(linhas);
    }

    public List<String> acoesDisponiveis(Notificacao notificacao, ContextoUtilizador contexto) {
        List<String> acoes = new ArrayList<>();
        if (internoSemAcesso(contexto) || notificacao.getEstado() != EstadoNotificacao.ATIVA) {
            return acoes;
        }
        if (recusaLeitura(notificacao, contexto) == null) {
            acoes.add(ACAO_LER);
        }
        if (recusaResolucao(notificacao, contexto) == null) {
            acoes.add(ACAO_RESOLVER);
        }
        if (recusaRemocao(notificacao, contexto) == null) {
            acoes.add(ACAO_REMOVER);
        }
        return acoes;
    }

    private static ConkordException recusaLeitura(Notificacao notificacao, ContextoUtilizador contexto) {
        return switch (notificacao.getTipo()) {
            case TEMPORARIA -> destinatario(notificacao, contexto) ? null
                    : new NotificacaoPerfilNaoAutorizadoException(
                            "Só o destinatário pode marcar esta notificação como lida.");
            case ALERTA -> master(contexto) ? null
                    : new NotificacaoPerfilNaoAutorizadoException(
                            "Só um utilizador Master pode marcar este alerta como lido.");
            default -> new NotificacaoTipoSemLeituraException();
        };
    }

    private static ConkordException recusaResolucao(Notificacao notificacao, ContextoUtilizador contexto) {
        if (!master(contexto)) {
            return new NotificacaoPerfilNaoAutorizadoException("Só um utilizador Master pode resolver esta notificação.");
        }
        return switch (notificacao.getTipo()) {
            case PEDIDO_APOIO -> null;
            case REGULARIZACAO_PROCEDIMENTO -> NotificacaoTipoSemResolucaoManualException.regularizacao();
            default -> NotificacaoTipoSemResolucaoManualException.outroTipo();
        };
    }

    private static ConkordException recusaRemocao(Notificacao notificacao, ContextoUtilizador contexto) {
        if (!master(contexto)) {
            return new NotificacaoPerfilNaoAutorizadoException("Só um utilizador Master pode remover esta notificação.");
        }
        return switch (notificacao.getTipo()) {
            case ESTATICA, ALERTA -> null;
            default -> new NotificacaoTipoNaoRemovivelException();
        };
    }

    private Notificacao visivel(UUID id, ContextoUtilizador contexto) {
        // TECNICO e VISITANTE não acedem ao Centro de Notificações: recebem o mesmo 404 de uma linha que não veem.
        if (internoSemAcesso(contexto)) {
            throw new NotificacaoNaoEncontradaException();
        }
        Specification<Notificacao> especificacao = Specification.<Notificacao>where(
                        (root, query, cb) -> cb.equal(root.get("id"), id))
                .and(visibilidade.visiveisPara(contexto))
                .and(visibilidade.emVigencia(Instant.now(relogio)));
        return notificacaoRepository.findOne(especificacao).orElseThrow(NotificacaoNaoEncontradaException::new);
    }

    private static void recusar(ConkordException recusa) {
        if (recusa != null) {
            throw recusa;
        }
    }

    private static void exigirAtiva(Notificacao notificacao) {
        if (notificacao.getEstado() != EstadoNotificacao.ATIVA) {
            throw NotificacaoEstadoInvalidoException.jaNaoAtiva();
        }
    }

    /** 0 linhas: outro pedido fechou a notificação entre a leitura e a atualização. */
    private void registar(Notificacao notificacao, String operacao, int linhas, ContextoUtilizador contexto) {
        if (linhas == 0) {
            throw NotificacaoEstadoInvalidoException.jaNaoAtiva();
        }
        log.info("Estado de notificação alterado: notificacaoId={}, tipo={}, operacao={}, linhasAtualizadas={}, "
                        + "utilizadorId={}", notificacao.getId(), notificacao.getTipo(), operacao, linhas,
                contexto.utilizadorId());
    }

    private NotificacaoResumoResponse resumo(UUID id) {
        Notificacao atualizada = notificacaoRepository.findById(id).orElseThrow(NotificacaoNaoEncontradaException::new);
        return NotificacaoResumoResponse.de(atualizada, total(mensagemRepository.contarPorNotificacao(List.of(id))),
                total(documentoRepository.contarPorNotificacao(List.of(id))));
    }

    private static long total(List<ContagemPorNotificacao> contagens) {
        return contagens.stream().mapToLong(ContagemPorNotificacao::getTotal).sum();
    }

    private static boolean internoSemAcesso(ContextoUtilizador contexto) {
        return contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER;
    }

    private static boolean master(ContextoUtilizador contexto) {
        return contexto.perfilInterno() == PerfilInterno.MASTER;
    }

    private static boolean destinatario(Notificacao notificacao, ContextoUtilizador contexto) {
        return notificacao.getUtilizador() != null
                && Objects.equals(notificacao.getUtilizador().getId(), contexto.utilizadorId());
    }
}
