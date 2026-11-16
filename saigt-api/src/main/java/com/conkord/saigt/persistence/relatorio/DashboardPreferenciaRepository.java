package com.conkord.saigt.persistence.relatorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DashboardPreferenciaRepository extends JpaRepository<DashboardPreferencia, DashboardPreferenciaId> {

    List<DashboardPreferencia> findByIdUtilizadorIdOrderByOrdemAsc(UUID utilizadorId);

    /**
     * Executada logo, antes de inserir as linhas novas: num só flush o Hibernate faz os INSERT antes dos DELETE
     * derivados, o que violaria a PK e a única de `ordem`.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DashboardPreferencia p where p.id.utilizadorId = :utilizadorId")
    int apagarDoUtilizador(@Param("utilizadorId") UUID utilizadorId);
}
