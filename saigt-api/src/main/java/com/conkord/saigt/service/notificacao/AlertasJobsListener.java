package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.service.instrumento.AlertaCaducidadeDinamicaEvento;
import com.conkord.saigt.service.instrumento.AvisoInatividadeProcessoEvento;
import com.conkord.saigt.service.instrumento.LimiarCaducidade;
import com.conkord.saigt.service.instrumento.ProcessoAnuladoPorInatividadeEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Os jobs de caducidade e de inatividade já confirmaram a sua transação quando estes métodos correm: uma falha aqui
 * fica no log e não desfaz o que o job decidiu, e a notificação perdida é um risco residual aceite.
 */
@Component
public class AlertasJobsListener {

    private static final Logger log = LoggerFactory.getLogger(AlertasJobsListener.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PROCEDIMENTO_REFERENCIA_CADUCIDADE = "DECISAO_INICIO";

    static final String TITULO_CADUCIDADE_PROXIMA = "Dinâmica próxima da data de caducidade";
    static final String TEXTO_CADUCIDADE_PROXIMA =
            "A dinâmica %s, com data limite de conclusão a %s, ainda não foi terminada.";
    static final String TITULO_CADUCADA = "Dinâmica caducada";
    static final String TEXTO_CADUCADA =
            "A dinâmica %s caducou a %s. Pode continuar a dinâmica ou recomeçá-la do zero.";
    static final String TITULO_AVISO_INATIVIDADE = "Processo em risco de anulação por inatividade";
    static final String TEXTO_AVISO_INATIVIDADE =
            "O processo %s (%s) será anulado automaticamente a %s caso não haja alterações ao mesmo.";
    static final String TITULO_ANULADO_INATIVIDADE = "Processo anulado por inatividade";
    static final String TEXTO_ANULADO_INATIVIDADE =
            "O processo %s (%s) foi anulado de forma automática por inatividade.";

    private final ProcessoRepository processoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final AlertaProcessoService alertaProcessoService;

    public AlertasJobsListener(ProcessoRepository processoRepository, NotificacaoRepository notificacaoRepository,
                               AlertaProcessoService alertaProcessoService) {
        this.processoRepository = processoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.alertaProcessoService = alertaProcessoService;
    }

    /** O processo de referência é a Decisão de início publicada: dela vêm o prazo, o autor e o contacto de tramitação. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlertaCaducidade(AlertaCaducidadeDinamicaEvento evento) {
        try {
            Optional<Processo> referencia = processoRepository
                    .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByDataPublicacaoDrDesc(
                            evento.dinamicaId(), PROCEDIMENTO_REFERENCIA_CADUCIDADE, EstadoProcesso.PUBLICADO);
            if (referencia.isEmpty()) {
                log.warn("Caducidade sem processo de Decisão de início publicado — dinamicaId={} limiar={}",
                        evento.dinamicaId(), evento.limiar());
                return;
            }
            Processo processo = referencia.get();
            String dinamica = identificacao(processo.getDinamica());
            String data = DATA.format(evento.dataCaducidade());
            List<Notificacao> criadas = evento.limiar() == LimiarCaducidade.DATA
                    ? alertaProcessoService.criarParaAutorEEntidade(processo, TITULO_CADUCADA,
                            TEXTO_CADUCADA.formatted(dinamica, data))
                    : alertaProcessoService.criarParaAutorEEntidade(processo, TITULO_CADUCIDADE_PROXIMA,
                            TEXTO_CADUCIDADE_PROXIMA.formatted(dinamica, data));
            log.info("Alerta de caducidade criado — dinamicaId={} limiar={} processoId={} linhas={} loteId={}",
                    evento.dinamicaId(), evento.limiar(), processo.getId(), criadas.size(), loteId(criadas));
        } catch (RuntimeException e) {
            log.error("Falha a criar o alerta de caducidade — dinamicaId={} limiar={}", evento.dinamicaId(),
                    evento.limiar(), e);
        }
    }

    /**
     * O job republica o mesmo limiar todos os dias até o processo mudar. Num processo `EM_SUBMISSAO` sem gravações
     * desde a avaliação, a única `TEMPORARIA` da plataforma criada depois do limiar é o próprio aviso.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAvisoInatividade(AvisoInatividadeProcessoEvento evento) {
        try {
            Processo processo = processoRepository.findById(evento.processoId()).orElse(null);
            if (processo == null || processo.getEstado() != EstadoProcesso.EM_SUBMISSAO
                    || !processo.getAtualizadoEm().equals(evento.atualizadoEmAvaliado())) {
                log.info("Aviso de inatividade sem notificação, processo alterado — processoId={} limiar={}",
                        evento.processoId(), evento.limiar());
                return;
            }
            if (notificacaoRepository.existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(
                    processo.getId(), TipoNotificacao.TEMPORARIA, evento.momentoLimiar())) {
                log.info("Aviso de inatividade sem notificação, já avisado — processoId={} limiar={}",
                        evento.processoId(), evento.limiar());
                return;
            }
            List<Notificacao> criadas = alertaProcessoService.criarParaAutorEEntidade(processo,
                    TITULO_AVISO_INATIVIDADE, TEXTO_AVISO_INATIVIDADE.formatted(processo.getNumeroProcesso(),
                            nomeProcedimento(processo), DATA.format(evento.dataAnulacaoPrevista())));
            log.info("Aviso de inatividade criado — processoId={} limiar={} linhas={} loteId={}",
                    evento.processoId(), evento.limiar(), criadas.size(), loteId(criadas));
        } catch (RuntimeException e) {
            log.error("Falha a criar o aviso de inatividade — processoId={} limiar={}", evento.processoId(),
                    evento.limiar(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAnuladoPorInatividade(ProcessoAnuladoPorInatividadeEvento evento) {
        try {
            Processo processo = processoRepository.findById(evento.processoId()).orElse(null);
            if (processo == null || processo.getEstado() != EstadoProcesso.ANULADO) {
                log.info("Anulação por inatividade sem notificação, processo não anulado — processoId={}",
                        evento.processoId());
                return;
            }
            List<Notificacao> criadas = alertaProcessoService.criarParaAutorEEntidade(processo,
                    TITULO_ANULADO_INATIVIDADE,
                    TEXTO_ANULADO_INATIVIDADE.formatted(processo.getNumeroProcesso(), nomeProcedimento(processo)));
            log.info("Anulação por inatividade notificada — processoId={} linhas={} loteId={}",
                    evento.processoId(), criadas.size(), loteId(criadas));
        } catch (RuntimeException e) {
            log.error("Falha a notificar a anulação por inatividade — processoId={} estado=ANULADO",
                    evento.processoId(), e);
        }
    }

    /** O descritivo é opcional na Elaboração; sem ele o texto fica só com o número. */
    static String identificacao(Dinamica dinamica) {
        String descritivo = dinamica.getDescritivo();
        return descritivo == null || descritivo.isBlank()
                ? dinamica.getNumeroDinamica()
                : dinamica.getNumeroDinamica() + " — " + descritivo.strip();
    }

    private static String nomeProcedimento(Processo processo) {
        return processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getNome();
    }

    private static Object loteId(List<Notificacao> criadas) {
        return criadas.isEmpty() ? null : criadas.get(0).getLoteId();
    }
}
