package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessoRejeicaoRepository extends JpaRepository<ProcessoRejeicao, Long> {

    @Query("SELECT max(r.sequencia) FROM ProcessoRejeicao r WHERE r.processo.id = :processoId")
    Optional<Integer> maiorSequencia(@Param("processoId") UUID processoId);

    List<ProcessoRejeicao> findByProcesso_IdOrderBySequenciaAsc(UUID processoId);

    Optional<ProcessoRejeicao> findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(UUID processoId,
                                                                                    OrigemRejeicao origem);

    /** Nativa, numa só ida à BD: recusas da DGT (as da INCM não entram) e linhas `SUBMETER` da auditoria. */
    @Query(value = "SELECT (SELECT COUNT(*) FROM processo_rejeicao WHERE origem = 'DGT') AS rejeicoes, "
            + "(SELECT COUNT(*) FROM auditoria WHERE tipo_ocorrencia = 'PROCESSO' AND acao = 'SUBMETER') AS submissoes",
            nativeQuery = true)
    RejeicoesSubmissoesProjecao contarRejeicoesDgtESubmissoes();

    interface RejeicoesSubmissoesProjecao {
        long getRejeicoes();

        long getSubmissoes();
    }
}
