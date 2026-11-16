package com.conkord.saigt.persistence.core;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TipoDinamicaRepository extends JpaRepository<TipoDinamica, Long> {

    /** Serializa a verificação da designação única entre pedidos simultâneos do mesmo tipo de Dinâmica. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TipoDinamica t WHERE t.id = :id")
    Optional<TipoDinamica> bloquear(@Param("id") Long id);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    Page<TipoDinamica> findByAtivo(Boolean ativo, Pageable pageable);
}
