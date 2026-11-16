package com.conkord.saigt.persistence.core;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SnitReferenciaRepository extends JpaRepository<SnitReferencia, Long> {

    List<SnitReferencia> findByLista(ListaReferenciaSnit lista);

    List<SnitReferencia> findByListaAndAtivoTrue(ListaReferenciaSnit lista);

    boolean existsByLista(ListaReferenciaSnit lista);

    // SQL nativo: o JPQL não alcança o `->>` do jsonb, e só o PostgreSQL o tem.
    @Query(value = """
            SELECT * FROM snit_referencia
            WHERE lista = 'MUNICIPALITIES' AND ativo = true AND dados_origem ->> 'nut2Code' = :nut2
            """, nativeQuery = true)
    List<SnitReferencia> findConcelhosAtivosDaNutII(@Param("nut2") String nut2);

    @Query(value = """
            SELECT codigo FROM snit_referencia
            WHERE lista = 'MUNICIPALITIES' AND ativo = true AND dados_origem ->> 'nut2Code' = :nut2Code
            """, nativeQuery = true)
    List<String> findCodigosMunicipiosDaNut2(@Param("nut2Code") String nut2Code);

    Optional<SnitReferencia> findByListaAndCodigo(ListaReferenciaSnit lista, String codigo);

    boolean existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit lista, String codigo);

    List<SnitReferencia> findByListaAndCodigoIn(ListaReferenciaSnit lista, Collection<String> codigos);
}
