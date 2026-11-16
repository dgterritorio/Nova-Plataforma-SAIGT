package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositoSnitManifestoRepository extends JpaRepository<DepositoSnitManifesto, UUID> {

    /** O último gravado é o que os endpoints do SNIT servem. */
    Optional<DepositoSnitManifesto> findFirstByProcessoIdOrderByCriadoEmDesc(UUID processoId);

    List<DepositoSnitManifesto> findByProcessoIdOrderByCriadoEmAsc(UUID processoId);
}
