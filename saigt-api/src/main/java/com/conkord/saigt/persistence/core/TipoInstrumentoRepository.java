package com.conkord.saigt.persistence.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TipoInstrumentoRepository extends JpaRepository<TipoInstrumento, Long> {

    List<TipoInstrumento> findByAtivoTrueOrderByCodigoAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    Page<TipoInstrumento> findByAtivo(Boolean ativo, Pageable pageable);
}
