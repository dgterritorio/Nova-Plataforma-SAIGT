package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regista a intenção de escrever no storage antes de o objeto ser tocado, para a limpeza noturna reconciliar o bucket.
 * A chave só tem o número do processo e o id do documento, nunca dados pessoais.
 */
@Service
public class IntencaoArmazenamentoService {

    private final ArmazenamentoIntencaoRepository repositorio;
    private final TransactionTemplate comCommitProprio;

    public IntencaoArmazenamentoService(ArmazenamentoIntencaoRepository repositorio,
                                        PlatformTransactionManager transactionManager) {
        this.repositorio = repositorio;
        this.comCommitProprio = new TransactionTemplate(transactionManager);
        this.comCommitProprio.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Gravação e cópia têm commit próprio: sobrevivem a uma reversão do chamador, porque o objeto pode já estar no
     * bucket. O apagamento entra na transação do chamador e só persiste se a remoção da linha fizer commit.
     */
    public void registar(String chaveObjeto, OperacaoArmazenamento operacao) {
        ArmazenamentoIntencao intencao = new ArmazenamentoIntencao(chaveObjeto, operacao);
        if (operacao == OperacaoArmazenamento.APAGAMENTO) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("A intenção de apagamento exige a transação do chamador.");
            }
            repositorio.save(intencao);
        } else {
            comCommitProprio.executeWithoutResult(estado -> repositorio.save(intencao));
        }
    }
}
