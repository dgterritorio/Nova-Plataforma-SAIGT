package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InstrumentoConcelhoRepository extends JpaRepository<InstrumentoConcelho, InstrumentoConcelho.Id> {

    List<InstrumentoConcelho> findByIdInstrumentoIdOrderByIdMunicipioCodigo(UUID instrumentoId);

    /** Os concelhos de vários Instrumentos numa só consulta. */
    List<InstrumentoConcelho> findByIdInstrumentoIdInOrderByIdMunicipioCodigo(Collection<UUID> instrumentoIds);

    @Query("""
            select count(c) > 0 from InstrumentoConcelho c
            where c.instrumento.tipoInstrumento.id = :tipoInstrumentoId
              and lower(c.instrumento.designacao) = lower(:designacao)
              and c.id.municipioCodigo in :concelhos""")
    boolean existeDesignacaoNosConcelhos(@Param("tipoInstrumentoId") Long tipoInstrumentoId,
                                         @Param("designacao") String designacao,
                                         @Param("concelhos") Collection<String> concelhos);
}
