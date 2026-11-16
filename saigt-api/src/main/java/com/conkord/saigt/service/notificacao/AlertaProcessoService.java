package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alertas de processo gerados pela plataforma. A «notificação à entidade» é uma linha por utilizador da entidade,
 * porque cada destinatário lê as suas pelo `utilizador_id`; sem `entidade` nem `perfilDestino`, para não duplicar
 * a visibilidade.
 */
@Service
public class AlertaProcessoService {

    private final NotificacaoRepository notificacaoRepository;
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    private final ApplicationEventPublisher publicador;

    public AlertaProcessoService(NotificacaoRepository notificacaoRepository,
                                 UtilizadorEntidadeRepository utilizadorEntidadeRepository,
                                 ApplicationEventPublisher publicador) {
        this.notificacaoRepository = notificacaoRepository;
        this.utilizadorEntidadeRepository = utilizadorEntidadeRepository;
        this.publicador = publicador;
    }

    @Transactional
    public List<Notificacao> criarParaAutorEEntidade(Processo processo, String titulo, String texto) {
        Map<UUID, Utilizador> destinatarios = new LinkedHashMap<>();
        destinatarios.put(processo.getCriadoPor().getId(), processo.getCriadoPor());
        utilizadorEntidadeRepository.findUtilizadoresByEntidadeId(processo.getEntidade().getId())
                .forEach(utilizador -> destinatarios.putIfAbsent(utilizador.getId(), utilizador));

        UUID loteId = destinatarios.size() > 1 ? UUID.randomUUID() : null;
        List<Notificacao> criadas = notificacaoRepository.saveAll(destinatarios.values().stream()
                .map(utilizador -> nova(processo, utilizador, titulo, texto, loteId))
                .toList());

        publicador.publishEvent(new NotificacaoCriadaEvento(criadas.stream().map(Notificacao::getId).toList(), loteId,
                true));
        return criadas;
    }

    /** Aviso a um só destinatário: sem lote, e a cópia por email segue a regra do `utilizador`, não a do processo. */
    @Transactional
    public Notificacao criarParaUtilizador(Processo processo, Utilizador destinatario, String titulo, String texto) {
        Notificacao criada = notificacaoRepository.save(nova(processo, destinatario, titulo, texto, null));
        publicador.publishEvent(new NotificacaoCriadaEvento(List.of(criada.getId()), null, false));
        return criada;
    }

    private static Notificacao nova(Processo processo, Utilizador utilizador, String titulo, String texto,
                                    UUID loteId) {
        Notificacao notificacao = new Notificacao(TipoNotificacao.TEMPORARIA, titulo, texto, null);
        notificacao.setProcesso(processo);
        notificacao.setUtilizador(utilizador);
        notificacao.setLoteId(loteId);
        return notificacao;
    }
}
