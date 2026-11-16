package com.conkord.saigt.persistence.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SgifrRedeDefesaRepository extends JpaRepository<SgifrRedeDefesa, Long> {

    Optional<SgifrRedeDefesa> findByCodigo(String codigo);

    List<SgifrRedeDefesa> findByAplicaPraTrueOrderByCodigo();

    List<SgifrRedeDefesa> findByAplicaPsaTrueOrderByCodigo();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM processo_rede_defesa WHERE rede_defesa_id = :redeId)",
            nativeQuery = true)
    boolean existeEmProcesso(@Param("redeId") Long redeId);
}
