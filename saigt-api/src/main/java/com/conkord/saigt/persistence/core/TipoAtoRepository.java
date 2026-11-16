package com.conkord.saigt.persistence.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoAtoRepository extends JpaRepository<TipoAto, Long> {

    /** Para `serie_dr = PRIMEIRA`. */
    List<TipoAto> findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull();

    /** Para `serie_dr = SEGUNDA_H`/`SEGUNDA_C`. */
    List<TipoAto> findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    Page<TipoAto> findByAtivo(Boolean ativo, Pageable pagina);
}
