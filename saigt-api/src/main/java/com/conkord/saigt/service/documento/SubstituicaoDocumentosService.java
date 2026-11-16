package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.DocumentoVersaoDesatualizadaException;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda em `SUBSTITUIDOS/` o ficheiro que sai de uso numa correção (recusado, desbloqueado ou output anterior do
 * GeoConform), para nunca se perder um ficheiro submetido; o documento fica SUBSTITUIDO. O original é tratado pela
 * retenção real do objeto e não pelo estado do documento: com uma versão retida não se pode apagar, fica só um marcador
 * de eliminação, e a cópia fica retida até à mesma data.
 */
@Service
public class SubstituicaoDocumentosService {

    private static final Logger log = LoggerFactory.getLogger(SubstituicaoDocumentosService.class);
    private static final int TAMANHO_REVISAO = 8;

    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final ArmazenamentoDocumentos armazenamento;
    private final IntencaoArmazenamentoService intencaoArmazenamento;
    private final LinkPublicoService linkPublicoService;

    public SubstituicaoDocumentosService(ProcessoDocumentoRepository processoDocumentoRepository,
                                         ArmazenamentoDocumentos armazenamento,
                                         IntencaoArmazenamentoService intencaoArmazenamento,
                                         LinkPublicoService linkPublicoService) {
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.armazenamento = armazenamento;
        this.intencaoArmazenamento = intencaoArmazenamento;
        this.linkPublicoService = linkPublicoService;
    }

    /**
     * Na transação de quem substitui. Uma falha da cópia sai antes de o documento mudar e reverte a operação; o
     * apagamento do original só acontece depois do commit e, se falhar, fica para a limpeza noturna.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessoDocumento moverParaSubstituidos(ProcessoDocumento documento) {
        String original = documento.getCaminhoFicheiro();
        String destino = CaminhoDocumento.substituido(documento.getProcesso().getNumeroProcesso(), documento.getOrigem(),
                documento.getId(), novaRevisao(), documento.getNomeFicheiro());
        Optional<Instant> retencaoOriginal = armazenamento.retidoAte(original, documento.getVersaoObjeto());

        intencaoArmazenamento.registar(destino, OperacaoArmazenamento.COPIA);
        String versaoCopia = armazenamento.copiar(original, documento.getVersaoObjeto(), destino);
        removerCopiaSeReverter(destino);

        documento.setCaminhoFicheiro(destino);
        documento.setVersaoObjeto(versaoCopia);
        documento.setEstadoValidacao(EstadoValidacao.SUBSTITUIDO);
        linkPublicoService.revogar(documento.getId(), MotivoRevogacaoLink.SUBSTITUICAO);
        try {
            processoDocumentoRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new DocumentoVersaoDesatualizadaException();
        }
        // Por último: uma cópia retida já não se apaga, e só o rollback de quem chama a deixaria órfã.
        retencaoOriginal.ifPresent(ate -> armazenamento.reter(destino, versaoCopia, ate));

        intencaoArmazenamento.registar(original, OperacaoArmazenamento.APAGAMENTO);
        tratarOriginalAposCommit(documento.getId(), original);
        log.info("Documento movido para SUBSTITUIDOS | processoDocumentoId={} destino={}", documento.getId(), destino);
        return documento;
    }

    private void tratarOriginalAposCommit(UUID documentoId, String original) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (armazenamento.versoes(original).stream().anyMatch(VersaoObjeto::retida)) {
                        armazenamento.marcarEliminado(original);
                        log.info("Original com versão retida: só marcador de eliminação | processoDocumentoId={} "
                                + "caminho={}", documentoId, original);
                    } else {
                        armazenamento.removerTodasVersoes(original);
                    }
                } catch (RuntimeException e) {
                    log.warn("Original não tratado após commit, fica para a limpeza noturna | processoDocumentoId={} "
                            + "caminho={}", documentoId, original, e);
                }
            }
        });
    }

    private void removerCopiaSeReverter(String destino) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    armazenamento.removerTodasVersoes(destino);
                } catch (RuntimeException e) {
                    log.warn("Cópia órfã não removida após rollback, fica para a limpeza noturna | caminho={}", destino, e);
                }
            }
        });
    }

    private static String novaRevisao() {
        return UUID.randomUUID().toString().substring(0, TAMANHO_REVISAO);
    }
}
