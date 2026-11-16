package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.CopiaDepositoIndisponivelException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Copia os anexos de uma notificação para documentos novos do processo, em `DEPOSITO` e sem item. O conteúdo passa
 * pelo backend, e não pela cópia no servidor, para calcular o SHA-256 que o anexo não tem. Corre na transação do
 * chamador: se ela reverter, os objetos copiados são apagados e a intenção registada antes fica para a limpeza noturna.
 */
@Service
public class CopiaAnexosNotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(CopiaAnexosNotificacaoService.class);
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String VERSAO_SEM_VERSIONAMENTO = "null";
    private static final int TAMANHO_MAXIMO_EXTENSAO = 10;

    private final NotificacaoDocumentoRepository notificacaoDocumentoRepository;
    private final ArmazenamentoDocumentos armazenamento;
    private final IntencaoArmazenamentoService intencaoArmazenamento;

    @PersistenceContext
    private EntityManager entityManager;

    public CopiaAnexosNotificacaoService(NotificacaoDocumentoRepository notificacaoDocumentoRepository,
                                         ArmazenamentoDocumentos armazenamento,
                                         IntencaoArmazenamentoService intencaoArmazenamento) {
        this.notificacaoDocumentoRepository = notificacaoDocumentoRepository;
        this.armazenamento = armazenamento;
        this.intencaoArmazenamento = intencaoArmazenamento;
    }

    /** Devolve o número de anexos copiados. Exige a transação do chamador. */
    public int copiarParaDeposito(Processo processo, UUID notificacaoId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("A cópia dos anexos exige a transação do chamador.");
        }
        List<NotificacaoDocumento> anexos = notificacaoDocumentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(notificacaoId);
        for (NotificacaoDocumento anexo : anexos) {
            copiar(processo, anexo);
        }
        entityManager.flush();
        return anexos.size();
    }

    private void copiar(Processo processo, NotificacaoDocumento anexo) {
        UUID documentoId = UUID.randomUUID();
        String destino = CaminhoDocumento.privado(processo.getNumeroProcesso(), CategoriaDocumento.DEPOSITO,
                OrigemDocumento.CARREGADO, documentoId, anexo.getNomeFicheiro());
        intencaoArmazenamento.registar(destino, OperacaoArmazenamento.COPIA);
        removerSeReverter(destino, documentoId);
        ObjetoCopiado copiado = gravarCopia(anexo, destino);
        entityManager.persist(new ProcessoDocumento(documentoId, processo, null, CategoriaDocumento.DEPOSITO,
                extensaoGuardada(anexo.getNomeFicheiro()), anexo.getNomeFicheiro(), destino, copiado.hashSha256(),
                copiado.versaoObjeto(), anexo.getTamanhoBytes(), anexo.getCriadoPor()));
    }

    /** Objeto do anexo em falta sai como 404 ({@code abrir}); qualquer outra falha do storage, como 503. */
    private ObjetoCopiado gravarCopia(NotificacaoDocumento anexo, String destino) {
        long tamanho = anexo.getTamanhoBytes();
        try (InputStream origem = armazenamento.abrir(anexo.getCaminhoFicheiro(), null, 0, tamanho);
             LeituraControlada leitura = new LeituraControlada(origem, tamanho)) {
            String versao = armazenamento.guardar(destino, leitura, CONTENT_TYPE);
            if (leitura.bytesLidos() != tamanho) {
                throw new StorageIndisponivelException("O objeto do anexo não tem o tamanho registado.", null);
            }
            return new ObjetoCopiado(leitura.sha256(), versao == null ? VERSAO_SEM_VERSIONAMENTO : versao);
        } catch (StorageIndisponivelException | UncheckedIOException e) {
            throw new CopiaDepositoIndisponivelException(e);
        } catch (IOException e) {
            throw new CopiaDepositoIndisponivelException(e);
        }
    }

    private void removerSeReverter(String caminho, UUID documentoId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    armazenamento.removerTodasVersoes(caminho);
                } catch (RuntimeException e) {
                    // Uma versão retida não se apaga; fica para a limpeza noturna, pela intenção registada antes.
                    log.warn("Cópia para depósito não removida após rollback — processoDocumentoId={}", documentoId, e);
                }
            }
        });
    }

    private static String extensaoGuardada(String nomeOriginal) {
        String extensao = CaminhoDocumento.extensao(nomeOriginal);
        return extensao.length() > TAMANHO_MAXIMO_EXTENSAO ? extensao.substring(0, TAMANHO_MAXIMO_EXTENSAO) : extensao;
    }

    private record ObjetoCopiado(String hashSha256, String versaoObjeto) {
    }
}
