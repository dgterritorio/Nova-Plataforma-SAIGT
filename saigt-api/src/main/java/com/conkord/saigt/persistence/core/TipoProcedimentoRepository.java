package com.conkord.saigt.persistence.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TipoProcedimentoRepository extends JpaRepository<TipoProcedimento, Long> {

    List<TipoProcedimento> findByAtivoTrueOrderByNomeAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    Page<TipoProcedimento> findByAtivo(Boolean ativo, Pageable pageable);
}
