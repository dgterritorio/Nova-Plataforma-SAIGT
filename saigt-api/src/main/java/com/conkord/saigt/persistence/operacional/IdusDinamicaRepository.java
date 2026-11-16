package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdusDinamicaRepository extends JpaRepository<IdusDinamica, UUID> {

    boolean existsByIdus(String idus);
}
