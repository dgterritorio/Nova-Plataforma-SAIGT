package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.FalhaTecnicaIncm;
import com.conkord.saigt.integracao.incm.GetActDetailsRequest;
import com.conkord.saigt.integracao.incm.GetActDetailsResponse;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.idus.IdusProcessoService;
import com.conkord.saigt.service.instrumento.CaducidadeDinamicaService;
import com.conkord.saigt.service.instrumento.DinamicaService;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import com.conkord.saigt.service.instrumento.RejeicaoProcessoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta à INCM o estado de um ato enviado e aplica a resposta ao Processo (BL-INT-003). Corre sem utilizador: as
 * transições entram na auditoria como ação do sistema. Um Processo de cada vez, cada um na sua transação, para a
 * falha de um não afetar os outros.
 */
@Service
public class ConsultaIncmService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaIncmService.class);
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_VALIDACAO_INCM = "VALIDACAO_INCM";
    private static final String ACAO_PUBLICACAO_INCM = "PUBLICACAO_INCM";
    private static final String ACAO_REJEICAO_INCM = "REJEICAO_INCM";
    private static final String SUBMISSION_REJECTED = "SubmissionRejected";
    private static final Set<EstadoProcesso> CONSULTAVEIS =
            EnumSet.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.EM_VALIDACAO_INCM);

    private final ProcessoRepository processoRepository;
    private final ClienteIncm clienteIncm;
    private final IncmComunicacaoService comunicacaoService;
    private final AuditoriaService auditoriaService;
    private final DinamicaService dinamicaService;
    private final CaducidadeDinamicaService caducidadeDinamicaService;
    private final RejeicaoProcessoService rejeicaoProcessoService;
    private final NumeroDepositoService numeroDepositoService;
    private final IdusProcessoService idusProcessoService;
    private final RetencaoDocumentosService retencaoDocumentosService;
    private final ApplicationEventPublisher publicador;

    public ConsultaIncmService(ProcessoRepository processoRepository, ClienteIncm clienteIncm,
                               IncmComunicacaoService comunicacaoService, AuditoriaService auditoriaService,
                               DinamicaService dinamicaService, CaducidadeDinamicaService caducidadeDinamicaService,
                               RejeicaoProcessoService rejeicaoProcessoService,
                               NumeroDepositoService numeroDepositoService, IdusProcessoService idusProcessoService,
                               RetencaoDocumentosService retencaoDocumentosService,
                               ApplicationEventPublisher publicador) {
        this.idusProcessoService = idusProcessoService;
        this.processoRepository = processoRepository;
        this.clienteIncm = clienteIncm;
        this.comunicacaoService = comunicacaoService;
        this.auditoriaService = auditoriaService;
        this.dinamicaService = dinamicaService;
        this.caducidadeDinamicaService = caducidadeDinamicaService;
        this.rejeicaoProcessoService = rejeicaoProcessoService;
        this.numeroDepositoService = numeroDepositoService;
        this.retencaoDocumentosService = retencaoDocumentosService;
        this.publicador = publicador;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DesfechoConsultaIncm consultar(UUID processoId) {
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null) {
            log.warn("Consulta à INCM de um Processo que não existe — processoId={}", processoId);
            return DesfechoConsultaIncm.SEM_ALTERACAO;
        }
        if (!CONSULTAVEIS.contains(processo.getEstado())) {
            return DesfechoConsultaIncm.SEM_ALTERACAO;
        }
        OptionalLong actIdentifier = actIdentifier(processo);
        if (actIdentifier.isEmpty()) {
            return DesfechoConsultaIncm.SEM_ALTERACAO;
        }

        Instant enviadoEm = Instant.now();
        ResultadoChamadaIncm<GetActDetailsResponse> resultado =
                clienteIncm.obterDetalhesAto(processoId, actIdentifier.getAsLong());
        Instant recebidoEm = resultado.httpStatus() == null ? null : Instant.now();
        DesfechoConsultaIncm desfecho = decidir(processoId, resultado);

        registarComunicacao(processoId, actIdentifier.getAsLong(), resultado, desfecho, enviadoEm, recebidoEm);
        return aplicar(processo, desfecho, resultado.corpo());
    }

    private OptionalLong actIdentifier(Processo processo) {
        try {
            return OptionalLong.of(Long.parseLong(processo.getIncmActIdentifier()));
        } catch (NumberFormatException e) {
            log.warn("incm_act_identifier vazio ou não numérico — processoId={} valor={}", processo.getId(),
                    processo.getIncmActIdentifier());
            return OptionalLong.empty();
        }
    }

    private static DesfechoConsultaIncm decidir(UUID processoId, ResultadoChamadaIncm<GetActDetailsResponse> resultado) {
        GetActDetailsResponse resposta = resultado.corpo();
        if (resultado.temFalhaTecnica() || resposta == null) {
            return DesfechoConsultaIncm.FALHA_TECNICA;
        }
        if (!resposta.isSuccess()) {
            log.warn("A INCM não devolveu o ato — processoId={}", processoId);
            return DesfechoConsultaIncm.SEM_ALTERACAO;
        }
        if (resposta.publication() != null) {
            return DesfechoConsultaIncm.PUBLICADO;
        }
        if (SUBMISSION_REJECTED.equals(resposta.processingStatus())) {
            return DesfechoConsultaIncm.ANULADO_INCM;
        }
        return DesfechoConsultaIncm.SEM_ALTERACAO;
    }

    private void registarComunicacao(UUID processoId, long actIdentifier,
                                     ResultadoChamadaIncm<GetActDetailsResponse> resultado, DesfechoConsultaIncm desfecho,
                                     Instant enviadoEm, Instant recebidoEm) {
        GetActDetailsResponse resposta = resultado.corpo();
        // Um 2xx sem corpo não vem classificado pelo cliente, mas é inutilizável como qualquer outra falha técnica.
        FalhaTecnicaIncm falhaTecnica = resposta == null && !resultado.temFalhaTecnica()
                ? FalhaTecnicaIncm.ERRO_HTTP
                : resultado.falhaTecnica();
        comunicacaoService.registar(new RegistoComunicacaoIncm(processoId, OperacaoIncm.GET_ACT_DETAILS, actIdentifier,
                comunicacaoService.submissaoCorrente(processoId), null, new GetActDetailsRequest(actIdentifier), resposta,
                resultado.httpStatus(), resposta == null ? null : resposta.isSuccess(),
                resposta == null ? null : resposta.processingStatus(), resposta == null ? null : resposta.actStatus(),
                falhaTecnica, desfecho.transicaoDerivada(), null, enviadoEm, recebidoEm));
    }

    private DesfechoConsultaIncm aplicar(Processo processo, DesfechoConsultaIncm desfecho, GetActDetailsResponse resposta) {
        if (desfecho.transicaoDerivada() == null) {
            return desfecho;
        }
        EstadoProcesso origem = processo.getEstado();
        List<PassoAuditoria> passos = desfecho == DesfechoConsultaIncm.PUBLICADO
                ? publicar(processo, resposta)
                : anular(processo, resposta);
        if (desfecho == DesfechoConsultaIncm.PUBLICADO) {
            // Uma falha do storage reverte a transação e a execução seguinte do job volta a tentar.
            retencaoDocumentosService.reterDocumentosSemRetencao(processo, MomentoRetencao.ENTRADA_PUBLICADO);
        }
        try {
            processoRepository.saveAndFlush(processo);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Processo alterado durante a consulta à INCM, fica para a execução seguinte — processoId={}",
                    processo.getId());
            // A exceção já marcou a transação como rollback-only; assim o commit faz rollback sem lançar
            // UnexpectedRollbackException.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return DesfechoConsultaIncm.SEM_ALTERACAO;
        }
        List<String> mensagens = desfecho == DesfechoConsultaIncm.ANULADO_INCM
                ? mensagensDaRecusa(resposta)
                : List.of();
        if (desfecho == DesfechoConsultaIncm.ANULADO_INCM) {
            rejeicaoProcessoService.registarRecusaIncm(processo, mensagens);
        }
        passos.forEach(passo -> auditoriaService.registarSistema(TIPO_OCORRENCIA, processo.getId().toString(),
                passo.acao(), passo.transicao(), passo.dados()));
        if (desfecho == DesfechoConsultaIncm.PUBLICADO) {
            idusProcessoService.registarPublicacaoIncm(processo);
            numeroDepositoService.atribuirNaPublicacao(processo);
            caducidadeDinamicaService.definirNaPublicacao(processo);
            dinamicaService.verificarConclusaoPeloSistema(processo.getDinamica());
        }
        // Um só evento por consulta: o EM_VALIDACAO_INCM intermédio de uma publicação não gera aviso.
        publicador.publishEvent(desfecho == DesfechoConsultaIncm.ANULADO_INCM
                ? ProcessoTransitouEvento.rejeicaoIncm(processo.getId(), origem, mensagens)
                : ProcessoTransitouEvento.de(processo.getId(), origem, EstadoProcesso.PUBLICADO));
        log.info("Consulta à INCM aplicada | processoId={} desfecho={} estado={}", processo.getId(), desfecho,
                processo.getEstado());
        return desfecho;
    }

    private List<PassoAuditoria> publicar(Processo processo, GetActDetailsResponse resposta) {
        List<PassoAuditoria> passos = new ArrayList<>();
        // A máquina de estados só chega a PUBLICADO a partir de EM_VALIDACAO_INCM.
        if (processo.getEstado() == EstadoProcesso.ENVIADO_INCM) {
            passos.add(transitar(processo, EstadoProcesso.EM_VALIDACAO_INCM, ACAO_VALIDACAO_INCM, null));
        }
        GetActDetailsResponse.Publication publicacao = resposta.publication();
        processo.setNumeroDr(resposta.actReferenceIdentifier());
        processo.setDataPublicacaoDr(publicacao.actPublicationDate());
        processo.setLinkDr(publicacao.actUrl());
        passos.add(transitar(processo, EstadoProcesso.PUBLICADO, ACAO_PUBLICACAO_INCM, null));
        return passos;
    }

    private List<PassoAuditoria> anular(Processo processo, GetActDetailsResponse resposta) {
        List<String> mensagens = resposta.actValidationErrorMessageList() == null
                ? List.of()
                : resposta.actValidationErrorMessageList();
        return List.of(transitar(processo, EstadoProcesso.ANULADO_INCM, ACAO_REJEICAO_INCM,
                Map.of("mensagens", mensagens)));
    }

    private static List<String> mensagensDaRecusa(GetActDetailsResponse resposta) {
        if (resposta.actValidationErrorMessageList() != null) {
            return resposta.actValidationErrorMessageList();
        }
        return resposta.errorMessage() == null ? List.of() : List.of(resposta.errorMessage());
    }

    private static PassoAuditoria transitar(Processo processo, EstadoProcesso destino, String acao, Object dados) {
        EstadoProcesso origem = processo.getEstado();
        if (!origem.podeTransitarPara(destino)) {
            throw new IllegalStateException("Transição fora da máquina de estados: " + origem + " -> " + destino);
        }
        processo.setEstado(destino);
        return new PassoAuditoria(acao, TransicaoEstado.de(origem, destino), dados);
    }

    private record PassoAuditoria(String acao, TransicaoEstado transicao, Object dados) {
    }
}
