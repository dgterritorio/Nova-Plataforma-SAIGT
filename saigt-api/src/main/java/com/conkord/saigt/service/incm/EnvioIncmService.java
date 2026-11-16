package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.VersaoDesatualizadaException;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.FalhaTecnicaIncm;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.integracao.incm.SubmitNewActRequest;
import com.conkord.saigt.integracao.incm.SubmitNewActResponse;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.instrumento.RejeicaoProcessoService;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Submissão de um processo `VALIDADO_DGT` à INCM (`SubmitNewAct`): o estado seguinte vem da resposta síncrona.
 */
@Service
public class EnvioIncmService {

    private static final Logger log = LoggerFactory.getLogger(EnvioIncmService.class);
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_ENVIAR_INCM = "ENVIAR_INCM";
    private static final String ACAO_SUBMISSAO_ACEITE = "SUBMISSAO_INCM_ACEITE";
    private static final String ACAO_SUBMISSAO_REJEITADA = "SUBMISSAO_INCM_REJEITADA";
    private static final String MENSAGEM_SEM_RESPOSTA = "A INCM não respondeu ao envio do ato. Tente novamente mais tarde.";
    private static final String MENSAGEM_CIRCUITO_ABERTO =
            "O envio à INCM está suspenso após falhas repetidas. Tente novamente dentro de alguns instantes.";

    private final ProcessoRepository processoRepository;
    private final MontadorPedidoAtoIncm montador;
    private final LinksPublicosIncm linksPublicosIncm;
    private final IncmComunicacaoService incmComunicacaoService;
    private final ClienteIncm clienteIncm;
    private final AuditoriaService auditoriaService;
    private final RejeicaoProcessoService rejeicaoProcessoService;
    private final RetencaoDocumentosService retencaoDocumentosService;
    private final ApplicationEventPublisher publicador;

    public EnvioIncmService(ProcessoRepository processoRepository, MontadorPedidoAtoIncm montador,
                            LinksPublicosIncm linksPublicosIncm,
                            IncmComunicacaoService incmComunicacaoService, ClienteIncm clienteIncm,
                            AuditoriaService auditoriaService, RejeicaoProcessoService rejeicaoProcessoService,
                            RetencaoDocumentosService retencaoDocumentosService,
                            ApplicationEventPublisher publicador) {
        this.processoRepository = processoRepository;
        this.montador = montador;
        this.linksPublicosIncm = linksPublicosIncm;
        this.incmComunicacaoService = incmComunicacaoService;
        this.clienteIncm = clienteIncm;
        this.auditoriaService = auditoriaService;
        this.rejeicaoProcessoService = rejeicaoProcessoService;
        this.retencaoDocumentosService = retencaoDocumentosService;
        this.publicador = publicador;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Processo enviar(UUID processoId, UUID utilizadorId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        if (processo.getEstado() != EstadoProcesso.VALIDADO_DGT) {
            throw new EstadoInvalidoException(
                    "Transição não permitida a partir do estado atual (" + processo.getEstado() + ").");
        }
        montador.validar(processo);
        // Antes do SubmitNewAct: uma falha do storage depois de a INCM ter o ato deixava-o sem processo correspondente.
        retencaoDocumentosService.reterDocumentosSemRetencao(processo, MomentoRetencao.SAIDA_VALIDADO_DGT);

        SubmitNewActRequest pedido = montador.montar(processo, linksPublicosIncm.preparar(processo));
        int submissao = incmComunicacaoService.submissaoParaNovoEnvio(processoId);

        Instant enviadoEm = Instant.now();
        ResultadoChamadaIncm<SubmitNewActResponse> resultado = clienteIncm.submeterAto(processoId, pedido);
        Instant recebidoEm = resultado.httpStatus() == null ? null : Instant.now();
        SubmitNewActResponse resposta = resultado.corpo();
        boolean semResultado = resultado.temFalhaTecnica() || resposta == null
                || (resposta.isSuccess() && resposta.actIdentifier() == null);
        EstadoProcesso destino = semResultado ? null
                : resposta.isSuccess() ? EstadoProcesso.EM_VALIDACAO_INCM : EstadoProcesso.ANULADO_INCM;

        incmComunicacaoService.registar(new RegistoComunicacaoIncm(processoId, OperacaoIncm.SUBMIT_NEW_ACT,
                resposta == null ? null : resposta.actIdentifier(), submissao,
                processo.serieDrEfetiva(), pedido, resposta, resultado.httpStatus(),
                resposta == null ? null : resposta.isSuccess(), null, null, resultado.falhaTecnica(), destino, null,
                enviadoEm, recebidoEm));

        if (destino == null) {
            log.warn("Envio à INCM sem resultado | processoId={} submissao={} falhaTecnica={} httpStatus={}",
                    processoId, submissao, resultado.falhaTecnica(), resultado.httpStatus());
            throw new IncmIndisponivelException(resultado.falhaTecnica() == FalhaTecnicaIncm.CIRCUITO_ABERTO
                    ? MENSAGEM_CIRCUITO_ABERTO : MENSAGEM_SEM_RESPOSTA);
        }

        transitar(processo, EstadoProcesso.VALIDADO_DGT, EstadoProcesso.ENVIADO_INCM);
        transitar(processo, EstadoProcesso.ENVIADO_INCM, destino);
        if (destino == EstadoProcesso.EM_VALIDACAO_INCM) {
            processo.setIncmActIdentifier(String.valueOf(resposta.actIdentifier()));
        }
        Processo guardado = salvar(processo);

        auditoriaService.registarTransicao(TIPO_OCORRENCIA, processoId.toString(), ACAO_ENVIAR_INCM,
                TransicaoEstado.de(EstadoProcesso.VALIDADO_DGT, EstadoProcesso.ENVIADO_INCM), utilizadorId, null, null);
        // ENVIADO_INCM é intermédio: um só evento, com o destino final.
        if (destino == EstadoProcesso.EM_VALIDACAO_INCM) {
            auditoriaService.registarTransicao(TIPO_OCORRENCIA, processoId.toString(), ACAO_SUBMISSAO_ACEITE,
                    TransicaoEstado.de(EstadoProcesso.ENVIADO_INCM, destino), utilizadorId, null, null);
            publicador.publishEvent(ProcessoTransitouEvento.de(processoId, EstadoProcesso.VALIDADO_DGT, destino));
        } else {
            List<String> mensagens = resposta.errorMessageList() == null ? List.of() : resposta.errorMessageList();
            rejeicaoProcessoService.registarRecusaIncm(guardado, mensagens);
            auditoriaService.registarTransicao(TIPO_OCORRENCIA, processoId.toString(), ACAO_SUBMISSAO_REJEITADA,
                    TransicaoEstado.de(EstadoProcesso.ENVIADO_INCM, destino), utilizadorId, null,
                    Map.of("mensagens", mensagens));
            publicador.publishEvent(ProcessoTransitouEvento.rejeicaoIncm(processoId, EstadoProcesso.VALIDADO_DGT,
                    mensagens));
        }
        log.info("Envio à INCM concluído | processoId={} submissao={} estado={} actIdentifier={}",
                processoId, submissao, destino, resposta.actIdentifier());
        return guardado;
    }

    private static void transitar(Processo processo, EstadoProcesso origem, EstadoProcesso destino) {
        if (processo.getEstado() != origem || !origem.podeTransitarPara(destino)) {
            throw new IllegalStateException("Transição fora da máquina de estados: " + processo.getEstado() + " -> " + destino);
        }
        processo.setEstado(destino);
    }

    private Processo salvar(Processo processo) {
        try {
            return processoRepository.saveAndFlush(processo);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new VersaoDesatualizadaException();
        }
    }
}
