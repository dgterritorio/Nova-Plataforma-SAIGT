package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.DocumentoNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.MensagemNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.NotificacaoDetalheResponse;
import com.conkord.saigt.dto.notificacao.NotificacaoFiltro;
import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.persistence.operacional.ContagemPorNotificacao;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificacaoConsultaService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoConsultaService.class);
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");
    private static final String ACAO_RESPONDER = "RESPONDER";
    private static final String ACAO_REABRIR = "REABRIR";
    private static final String ACAO_ANEXAR = "ANEXAR";

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMensagemRepository mensagemRepository;
    private final NotificacaoDocumentoRepository documentoRepository;
    private final NotificacaoVisibilidade visibilidade;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final NotificacaoMensagemService mensagemService;
    private final NotificacaoEstadoService estadoService;
    private final NotificacaoAnexoService anexoService;
    private final Clock relogio;

    public NotificacaoConsultaService(NotificacaoRepository notificacaoRepository,
                                      NotificacaoMensagemRepository mensagemRepository,
                                      NotificacaoDocumentoRepository documentoRepository,
                                      NotificacaoVisibilidade visibilidade,
                                      ContextoUtilizadorResolver contextoUtilizadorResolver,
                                      NotificacaoMensagemService mensagemService,
                                      NotificacaoEstadoService estadoService, NotificacaoAnexoService anexoService,
                                      Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.mensagemRepository = mensagemRepository;
        this.documentoRepository = documentoRepository;
        this.visibilidade = visibilidade;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.mensagemService = mensagemService;
        this.estadoService = estadoService;
        this.anexoService = anexoService;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResumoResponse> listar(List<String> estado, List<String> tipo, UUID processoId,
                                                  LocalDate desde, LocalDate ate, Pageable pageable) {
        ContextoUtilizador contexto = exigirAcesso();
        NotificacaoFiltro filtro = NotificacaoFiltro.de(estado, tipo, processoId, desde, ate);

        Specification<Notificacao> especificacao = visibilidade.visiveisPara(contexto).and(filtrar(filtro));
        if (filtro.semEstado()) {
            especificacao = especificacao.and(visibilidade.emVigencia(Instant.now(relogio)));
        }
        Page<Notificacao> pagina = notificacaoRepository.findAll(especificacao, pageable);

        List<UUID> ids = pagina.getContent().stream().map(Notificacao::getId).toList();
        Map<UUID, Long> mensagens = ids.isEmpty() ? Map.of() : porNotificacao(mensagemRepository.contarPorNotificacao(ids));
        Map<UUID, Long> documentos = ids.isEmpty() ? Map.of() : porNotificacao(documentoRepository.contarPorNotificacao(ids));

        log.debug("Listagem de notificações: utilizadorId={}, resultados={}, total={}, estados={}, tipos={}, "
                        + "processoId={}, desde={}, ate={}", contexto.utilizadorId(), ids.size(),
                pagina.getTotalElements(), filtro.estados(), filtro.tipos(), filtro.processoId(), filtro.desde(),
                filtro.ate());
        return pagina.map(notificacao -> NotificacaoResumoResponse.de(notificacao,
                mensagens.getOrDefault(notificacao.getId(), 0L), documentos.getOrDefault(notificacao.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public NotificacaoDetalheResponse obter(UUID id) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        // TECNICO e VISITANTE não acedem ao Centro de Notificações: o detalhe não distingue a linha que não veem.
        if (contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new NotificacaoNaoEncontradaException();
        }
        Specification<Notificacao> especificacao = Specification.<Notificacao>where(
                        (root, query, cb) -> cb.equal(root.get("id"), id))
                .and(visibilidade.visiveisPara(contexto))
                .and(visibilidade.emVigencia(Instant.now(relogio)));
        Notificacao notificacao = notificacaoRepository.findOne(especificacao)
                .orElseThrow(NotificacaoNaoEncontradaException::new);

        List<MensagemNotificacaoResponse> mensagens = mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(id)
                .stream().map(MensagemNotificacaoResponse::de).toList();
        List<DocumentoNotificacaoResponse> documentos = documentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(id)
                .stream().map(DocumentoNotificacaoResponse::de).toList();
        List<String> acoesDisponiveis = new ArrayList<>();
        if (mensagemService.podeResponder(notificacao, contexto)) {
            acoesDisponiveis.add(ACAO_RESPONDER);
        }
        if (NotificacaoReaberturaService.podeReabrir(notificacao, contexto)) {
            acoesDisponiveis.add(ACAO_REABRIR);
        }
        if (anexoService.podeAnexar(notificacao, contexto)) {
            acoesDisponiveis.add(ACAO_ANEXAR);
        }
        acoesDisponiveis.addAll(estadoService.acoesDisponiveis(notificacao, contexto));
        return NotificacaoDetalheResponse.de(notificacao, mensagens, documentos, acoesDisponiveis);
    }

    private ContextoUtilizador exigirAcesso() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Perfil sem acesso ao Centro de Notificações.");
        }
        return contexto;
    }

    private static Specification<Notificacao> filtrar(NotificacaoFiltro filtro) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("processo", JoinType.LEFT);
                root.fetch("criadoPor", JoinType.LEFT);
            }
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(filtro.semEstado()
                    ? cb.equal(root.get("estado"), EstadoNotificacao.ATIVA)
                    : root.get("estado").in(filtro.estados()));
            if (!filtro.tipos().isEmpty()) {
                predicados.add(root.get("tipo").in(filtro.tipos()));
            }
            if (filtro.processoId() != null) {
                predicados.add(cb.equal(root.get("processo").get("id"), filtro.processoId()));
            }
            if (filtro.desde() != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("criadoEm"),
                        filtro.desde().atStartOfDay(ZONE_LISBOA).toInstant()));
            }
            if (filtro.ate() != null) {
                predicados.add(cb.lessThan(root.get("criadoEm"),
                        filtro.ate().plusDays(1).atStartOfDay(ZONE_LISBOA).toInstant()));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static Map<UUID, Long> porNotificacao(List<ContagemPorNotificacao> contagens) {
        return contagens.stream().collect(Collectors.toMap(ContagemPorNotificacao::getNotificacaoId,
                ContagemPorNotificacao::getTotal));
    }
}
