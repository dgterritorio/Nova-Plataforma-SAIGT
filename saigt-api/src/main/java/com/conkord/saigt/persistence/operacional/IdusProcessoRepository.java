package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdusProcessoRepository extends JpaRepository<IdusProcesso, UUID> {

    /** Usa `uq_idus_processo_idus`. */
    Optional<IdusProcesso> findByIdus(String idus);

    boolean existsByIdus(String idus);

    /** O IDUS de qualquer outro Processo da Dinâmica: partilham o ID_IGT e a Ordem_Dinâmica. */
    Optional<IdusProcesso> findFirstByProcessoDinamicaId(UUID dinamicaId);

    List<IdusProcesso> findByProcessoDinamicaId(UUID dinamicaId);
}
