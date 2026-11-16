package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositoSnitManifestoLinhaRepository
        extends JpaRepository<DepositoSnitManifestoLinha, DepositoSnitManifestoLinha.Id> {

    List<DepositoSnitManifestoLinha> findByIdManifestoId(UUID manifestoId);
}
