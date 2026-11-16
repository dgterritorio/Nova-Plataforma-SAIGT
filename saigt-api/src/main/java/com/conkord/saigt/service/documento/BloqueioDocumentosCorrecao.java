package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ao iniciar a correção, só se altera o que a DGT recusou: depois de uma rejeição, os ficheiros validados ficam
 * bloqueados; depois de uma anulação pela INCM, ficam todos. O valor é recalculado em cada correção.
 */
@Service
public class BloqueioDocumentosCorrecao {

    private static final Logger log = LoggerFactory.getLogger(BloqueioDocumentosCorrecao.class);

    private final ProcessoDocumentoRepository processoDocumentoRepository;

    public BloqueioDocumentosCorrecao(ProcessoDocumentoRepository processoDocumentoRepository) {
        this.processoDocumentoRepository = processoDocumentoRepository;
    }

    /** Corre na transação do `corrigir`, para reverter com ela. Devolve o número de ficheiros bloqueados. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long bloquearParaCorrecao(Processo processo, EstadoProcesso origem) {
        switch (origem) {
            case REJEITADO_DGT -> processoDocumentoRepository.bloquearSoValidados(processo.getId(), EstadoValidacao.VALIDADO);
            case ANULADO_INCM -> processoDocumentoRepository.bloquearTodos(processo.getId());
            default -> throw new IllegalArgumentException("Origem sem regra de bloqueio para correção: " + origem);
        }
        // O retorno do update conta todas as linhas do Processo, não as bloqueadas.
        long bloqueados = processoDocumentoRepository.contarBloqueadosAtuais(processo.getId());
        log.info("Ficheiros bloqueados para correção | processoId={} origem={} documentosBloqueados={}",
                processo.getId(), origem, bloqueados);
        return bloqueados;
    }
}
