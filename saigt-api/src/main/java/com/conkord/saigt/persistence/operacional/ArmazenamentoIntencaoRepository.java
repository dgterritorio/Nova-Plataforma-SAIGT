package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ArmazenamentoIntencaoRepository extends JpaRepository<ArmazenamentoIntencao, Long> {

    List<ArmazenamentoIntencao> findByChaveObjeto(String chaveObjeto);

    /** Por id crescente a partir de `depoisDe`: as intenções que ficam por falha não voltam no lote seguinte. */
    List<ArmazenamentoIntencao> findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(Instant limite,
                                                                                          Long depoisDe);
}
