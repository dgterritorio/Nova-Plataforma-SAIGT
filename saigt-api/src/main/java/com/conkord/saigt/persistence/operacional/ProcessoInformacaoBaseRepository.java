package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessoInformacaoBaseRepository extends JpaRepository<ProcessoInformacaoBase, UUID> {

    List<ProcessoInformacaoBase> findByProcesso_Id(UUID processoId);

    Optional<ProcessoInformacaoBase> findByIdAndProcesso_Id(UUID id, UUID processoId);
}
