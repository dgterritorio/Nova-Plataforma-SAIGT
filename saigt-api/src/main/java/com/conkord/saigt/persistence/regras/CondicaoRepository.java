package com.conkord.saigt.persistence.regras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface CondicaoRepository extends JpaRepository<Condicao, Long> {

    List<Condicao> findByRegraAndAtivoTrue(Regra regra);

    /** As condições de uma página de regras numa só consulta, ativas e inativas. */
    List<Condicao> findByRegraInOrderByGrupoAscIdAsc(Collection<Regra> regras);

    /** Validação de arranque dos parâmetros: as inativas não são avaliadas. */
    List<Condicao> findByAtivoTrue();

    /** Validação de arranque: cada valor tem de corresponder a um avaliador implementado. */
    @Query("SELECT DISTINCT c.avaliador FROM Condicao c")
    List<String> findAvaliadoresDistintos();

    /** Qualquer condição, ativa ou não: o validador de arranque também lê os códigos citados nas inativas. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM condicao WHERE parametros->>'codigo' = :codigo)", nativeQuery = true)
    boolean existeParametroCodigo(@Param("codigo") String codigo);
}
