package com.conkord.saigt.persistence.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NomeDocumentoRepository extends JpaRepository<NomeDocumento, Long> {

    List<NomeDocumento> findByAtivoTrueOrderByOrdemAsc();
}
