package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessoMetadadosRepository extends JpaRepository<ProcessoMetadados, UUID> {
}
