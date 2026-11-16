package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.ProcessoEnviadoPcmEvento;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avisos das transições de `processo.estado` e do «Enviar para PCM», criados depois do commit da transição: uma falha
 * aqui fica no log e não desfaz a transição, e a notificação perdida é um risco residual aceite. Os textos levam só
 * números, nomes de procedimento, estados, motivos e a ligação ao DR — nunca nomes de pessoas nem emails.
 */
@Component
public class AlertasTransicaoListener {

    private static final Logger log = LoggerFactory.getLogger(AlertasTransicaoListener.class);
    private static final String TIPO_OCORRENCIA_PROCESSO = "PROCESSO";
    private static final String ACAO_ENVIAR_INCM = "ENVIAR_INCM";

    static final String TITULO_SUBMETIDO = "Processo submetido";
    static final String TEXTO_SUBMETIDO = "O processo %s (%s) foi submetido.";
    static final String TITULO_REJEITADO_DGT = "Processo rejeitado pela DGT";
    static final String TEXTO_REJEITADO_DGT = "O processo %s (%s) foi rejeitado pela DGT%s%s.";
    static final String TRECHO_ITENS = " nos itens %s";
    static final String TRECHO_MOTIVO = " com o motivo %s";
    static final String TITULO_ANULADO_DGT = "Processo anulado pela DGT";
    static final String TEXTO_ANULADO_DGT = "O processo %s (%s) foi anulado pela DGT%s.";
    static final String TITULO_REJEITADO_INCM = "Processo rejeitado pela INCM";
    static final String TEXTO_REJEITADO_INCM = "O processo %s (%s) foi rejeitado pela INCM com o motivo %s.";
    static final String MOTIVO_INCM_EM_FALTA = "motivo não indicado pela INCM";
    static final String TITULO_PUBLICADO = "Processo publicado";
    static final String TEXTO_PUBLICADO = "O processo %s (%s) foi publicado em Diário da República: %s.";
    static final String TEXTO_PUBLICADO_SEM_LIGACAO = "O processo %s (%s) foi publicado em Diário da República.";
    static final String TITULO_ALTERACAO_ESTADO = "Alteração do estado do processo";
    static final String TEXTO_ALTERACAO_ESTADO = "O processo %s (%s) passou ao estado %s.";
    static final String TITULO_PCM = "Processo para aprovação da PCM";
    static final String TEXTO_PCM = "O processo %s (%s) aguarda aprovação da PCM.";
    static final String SEPARADOR_ITENS = ", ";
    static final String SEPARADOR_MENSAGENS_INCM = "; ";

    /** Os estados que só avisam o autor, com o nome da EF. */
    static final Map<EstadoProcesso, String> NOME_ESTADO = new EnumMap<>(Map.of(
            EstadoProcesso.EM_SUBMISSAO, "Em Submissão",
            EstadoProcesso.EM_VALIDACAO_DGT, "Em Validação DGT",
            EstadoProcesso.VALIDADO_DGT, "Validado DGT",
            EstadoProcesso.EM_VALIDACAO_INCM, "Em Validação INCM"));

    private static final Map<ElementoInformacaoBase, String> NOME_INFORMACAO_BASE = new EnumMap<>(Map.of(
            ElementoInformacaoBase.DELIBERACAO, "Informação Base da Deliberação",
            ElementoInformacaoBase.TEXTO_ATO, "Informação Base do Texto do Ato"));

    private final ProcessoRepository processoRepository;
    private final ProcessoRejeicaoItemRepository rejeicaoItemRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AlertaProcessoService alertaProcessoService;

    public AlertasTransicaoListener(ProcessoRepository processoRepository,
                                    ProcessoRejeicaoItemRepository rejeicaoItemRepository,
                                    AuditoriaRepository auditoriaRepository, UtilizadorRepository utilizadorRepository,
                                    AlertaProcessoService alertaProcessoService) {
        this.processoRepository = processoRepository;
        this.rejeicaoItemRepository = rejeicaoItemRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.alertaProcessoService = alertaProcessoService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoTransitar(ProcessoTransitouEvento evento) {
        try {
            Processo processo = processoRepository.findById(evento.processoId()).orElse(null);
            if (processo == null || processo.getEstado() != evento.destino()) {
                log.info("Transição sem notificação, estado mudou entretanto — evento=ProcessoTransitouEvento "
                        + "processoId={} destino={}", evento.processoId(), evento.destino());
                return;
            }
            switch (evento.destino()) {
                case ENVIADO_DGT -> paraAutorEEntidade(evento, processo, TITULO_SUBMETIDO,
                        TEXTO_SUBMETIDO.formatted(numero(processo), tipo(processo)));
                case REJEITADO_DGT -> paraAutorEEntidade(evento, processo, TITULO_REJEITADO_DGT,
                        textoRejeicaoDgt(processo, evento.rejeicaoId()));
                case ANULADO -> paraAutorEEntidade(evento, processo, TITULO_ANULADO_DGT,
                        TEXTO_ANULADO_DGT.formatted(numero(processo), tipo(processo), trechoMotivo(evento.motivo())));
                case ANULADO_INCM -> rejeicaoIncm(evento, processo);
                case PUBLICADO -> paraAutorEEntidade(evento, processo, TITULO_PUBLICADO, textoPublicado(processo));
                case EM_SUBMISSAO, EM_VALIDACAO_DGT, VALIDADO_DGT, EM_VALIDACAO_INCM -> paraAutor(evento, processo);
                default -> log.info("Transição sem notificação prevista — evento=ProcessoTransitouEvento processoId={} "
                        + "destino={}", evento.processoId(), evento.destino());
            }
        } catch (RuntimeException e) {
            log.error("Falha a notificar a transição — evento=ProcessoTransitouEvento processoId={} destino={}",
                    evento.processoId(), evento.destino(), e);
        }
    }

    /** Sem `pcm_atribuido_a` igual ao do evento (devolvido ou reatribuído entretanto), não há aviso. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEnviarPcm(ProcessoEnviadoPcmEvento evento) {
        try {
            Processo processo = processoRepository.findById(evento.processoId()).orElse(null);
            Utilizador destinatario = processo == null ? null : processo.getPcmAtribuidoA();
            if (destinatario == null || !destinatario.getId().equals(evento.utilizadorPcmId())
                    || processo.getEstado() != EstadoProcesso.VALIDADO_DGT) {
                log.info("Envio à PCM sem notificação, estado mudou entretanto — evento=ProcessoEnviadoPcmEvento "
                        + "processoId={}", evento.processoId());
                return;
            }
            alertaProcessoService.criarParaUtilizador(processo, destinatario, TITULO_PCM,
                    TEXTO_PCM.formatted(numero(processo), tipo(processo)));
            log.info("Notificação de transição criada — evento=ProcessoEnviadoPcmEvento processoId={} "
                    + "destino=PCM linhas=1 loteId=null", evento.processoId());
        } catch (RuntimeException e) {
            log.error("Falha a notificar o envio à PCM — evento=ProcessoEnviadoPcmEvento processoId={}",
                    evento.processoId(), e);
        }
    }

    private void paraAutorEEntidade(ProcessoTransitouEvento evento, Processo processo, String titulo, String texto) {
        List<Notificacao> criadas = alertaProcessoService.criarParaAutorEEntidade(processo, titulo, texto);
        log.info("Notificação de transição criada — evento=ProcessoTransitouEvento processoId={} destino={} "
                + "linhas={} loteId={}", evento.processoId(), evento.destino(), criadas.size(), loteId(criadas));
    }

    private void paraAutor(ProcessoTransitouEvento evento, Processo processo) {
        alertaProcessoService.criarParaUtilizador(processo, processo.getCriadoPor(), TITULO_ALTERACAO_ESTADO,
                TEXTO_ALTERACAO_ESTADO.formatted(numero(processo), tipo(processo),
                        NOME_ESTADO.get(evento.destino())));
        log.info("Notificação de transição criada — evento=ProcessoTransitouEvento processoId={} destino={} "
                + "linhas=1 loteId=null", evento.processoId(), evento.destino());
    }

    /**
     * Autor e entidade num lote e, à parte, o utilizador DGT que enviou o processo à INCM: o da última linha
     * `ENVIAR_INCM` da auditoria, que vale tanto para a recusa no envio como para a da consulta diária.
     */
    private void rejeicaoIncm(ProcessoTransitouEvento evento, Processo processo) {
        String texto = TEXTO_REJEITADO_INCM.formatted(numero(processo), tipo(processo),
                mensagensIncm(evento.mensagensIncm()));
        List<Notificacao> criadas = alertaProcessoService.criarParaAutorEEntidade(processo, TITULO_REJEITADO_INCM,
                texto);
        Optional<Utilizador> dgt = utilizadorQueEnviouAIncm(processo);
        if (dgt.isEmpty()) {
            log.warn("Rejeição INCM sem utilizador DGT do envio à INCM, aviso à DGT não criado — processoId={}",
                    evento.processoId());
        } else if (criadas.stream().noneMatch(n -> n.getUtilizador().getId().equals(dgt.get().getId()))) {
            alertaProcessoService.criarParaUtilizador(processo, dgt.get(), TITULO_REJEITADO_INCM, texto);
        }
        log.info("Notificação de transição criada — evento=ProcessoTransitouEvento processoId={} destino={} "
                        + "linhas={} loteId={} avisoDgt={}", evento.processoId(), evento.destino(), criadas.size(),
                loteId(criadas), dgt.isPresent());
    }

    private Optional<Utilizador> utilizadorQueEnviouAIncm(Processo processo) {
        return auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                        TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), ACAO_ENVIAR_INCM)
                .map(Auditoria::getUtilizadorId)
                .flatMap(utilizadorRepository::findById);
    }

    /** Os itens e os motivos são os da recusa: um item `REJEITADO` tem sempre a sua linha nela. */
    private String textoRejeicaoDgt(Processo processo, Long rejeicaoId) {
        List<ProcessoRejeicaoItem> itens = rejeicaoId == null ? List.of()
                : rejeicaoItemRepository.findByRejeicao_IdOrderByIdAsc(rejeicaoId);
        String nomes = itens.stream().map(AlertasTransicaoListener::nomeDoItem).filter(Objects::nonNull)
                .collect(Collectors.joining(SEPARADOR_ITENS));
        String motivos = itens.stream().map(ProcessoRejeicaoItem::getMotivo).filter(Objects::nonNull)
                .map(ListaValores::getDescricao).distinct().collect(Collectors.joining(SEPARADOR_ITENS));
        return TEXTO_REJEITADO_DGT.formatted(numero(processo), tipo(processo),
                nomes.isEmpty() ? "" : TRECHO_ITENS.formatted(nomes), trechoMotivo(motivos));
    }

    private static String nomeDoItem(ProcessoRejeicaoItem item) {
        if (item.getProcessoDocumento() != null) {
            return item.getProcessoDocumento().getNomeFicheiro();
        }
        return item.getProcessoInformacaoBase() == null ? null
                : NOME_INFORMACAO_BASE.get(item.getProcessoInformacaoBase().getElemento());
    }

    private static String trechoMotivo(String motivo) {
        return motivo == null || motivo.isBlank() ? "" : TRECHO_MOTIVO.formatted(semPontoFinal(motivo));
    }

    /** O texto fecha com ponto: um motivo ou uma mensagem da INCM que já acabam em ponto não o repetem. */
    private static String semPontoFinal(String texto) {
        String limpo = texto.strip();
        return limpo.endsWith(".") ? limpo.substring(0, limpo.length() - 1).strip() : limpo;
    }

    static String mensagensIncm(List<String> mensagens) {
        String juntas = mensagens.stream().filter(Objects::nonNull).map(String::strip).filter(m -> !m.isEmpty())
                .collect(Collectors.joining(SEPARADOR_MENSAGENS_INCM));
        return juntas.isEmpty() ? MOTIVO_INCM_EM_FALTA : semPontoFinal(juntas);
    }

    private static String textoPublicado(Processo processo) {
        String ligacao = processo.getLinkDr();
        return ligacao == null || ligacao.isBlank()
                ? TEXTO_PUBLICADO_SEM_LIGACAO.formatted(numero(processo), tipo(processo))
                : TEXTO_PUBLICADO.formatted(numero(processo), tipo(processo), ligacao.strip());
    }

    private static String numero(Processo processo) {
        return processo.getNumeroProcesso();
    }

    private static String tipo(Processo processo) {
        return processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getNome();
    }

    private static UUID loteId(List<Notificacao> criadas) {
        return criadas.isEmpty() ? null : criadas.get(0).getLoteId();
    }
}
