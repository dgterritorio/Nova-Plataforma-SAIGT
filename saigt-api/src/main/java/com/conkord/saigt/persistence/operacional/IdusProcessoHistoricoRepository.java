package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IdusProcessoHistoricoRepository extends JpaRepository<IdusProcessoHistorico, Long> {

    /** Usa `idx_idus_processo_historico_processo_id`. */
    List<IdusProcessoHistorico> findByProcessoIdOrderByRegistadoEmAsc(UUID processoId);

    /** Por ordem de inserção: as mudanças do mesmo evento têm o mesmo `registadoEm`. */
    List<IdusProcessoHistorico> findByProcessoIdInAndEventoOrderByIdAsc(Collection<UUID> processoIds, EventoIdus evento);
}
