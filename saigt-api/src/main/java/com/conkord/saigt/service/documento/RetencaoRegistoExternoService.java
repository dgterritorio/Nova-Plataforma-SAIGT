package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Retenção da entrada em `PUBLICADO` de um processo do registo externo, que nasce publicado. Corre depois do commit e
 * não na transação do registo, porque uma versão retida já não se apaga se a transação reverter. Uma falha não desfaz
 * o registo: o job repete o passo nos processos sem a auditoria da retenção, que só se grava quando ela termina.
 */
@Service
public class RetencaoRegistoExternoService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoRegistoExternoService.class);
    private static final String ORIGEM_DADOS_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_RETER = "RETER_DOCUMENTOS";

    private final ProcessoRepository processoRepository;
    private final RetencaoDocumentosService retencaoDocumentosService;
    private final TransactionTemplate transacaoPropria;

    public RetencaoRegistoExternoService(ProcessoRepository processoRepository,
                                         RetencaoDocumentosService retencaoDocumentosService,
                                         PlatformTransactionManager transactionManager) {
        this.processoRepository = processoRepository;
        this.retencaoDocumentosService = retencaoDocumentosService;
        this.transacaoPropria = new TransactionTemplate(transactionManager);
        this.transacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Exige a transação do chamador; sem commit, não retém nada. */
    public void reterAposCommit(UUID processoId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reter(processoId);
            }
        });
    }

    /** Idempotente: as versões já retidas não são tocadas. Falso quando falhou e fica para o job. */
    public boolean reter(UUID processoId) {
        try {
            transacaoPropria.executeWithoutResult(estado -> processoRepository.findById(processoId).ifPresent(
                    processo -> retencaoDocumentosService.reterDocumentosSemRetencao(processo,
                            MomentoRetencao.ENTRADA_PUBLICADO)));
            return true;
        } catch (RuntimeException e) {
            log.warn("Retenção do registo externo por fazer, fica para a repetição | processoId={}", processoId, e);
            return false;
        }
    }

    /** O passo repetido pelo job `retencao-registo-externo`. */
    public int repetirPendentes() {
        List<UUID> pendentes = processoRepository.idsSemAuditoria(ORIGEM_DADOS_REGISTO_EXTERNO,
                EstadoProcesso.PUBLICADO, TIPO_OCORRENCIA, ACAO_RETER);
        int concluidos = 0;
        for (UUID processoId : pendentes) {
            if (reter(processoId)) {
                concluidos++;
            }
        }
        if (!pendentes.isEmpty()) {
            log.info("Repetição da retenção do registo externo | pendentes={} concluidos={}", pendentes.size(), concluidos);
        }
        return concluidos;
    }
}
