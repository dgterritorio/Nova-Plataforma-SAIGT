package com.conkord.saigt.persistence.regras;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegraRepository extends JpaRepository<Regra, Long> {

    /** Várias Regras por Dinâmica; todas têm de passar. */
    List<Regra> findByInstrumentoDinamicaConfigAndAtivoTrue(InstrumentoDinamicaConfig instrumentoDinamicaConfig);

    /** Ausência de Regra passa vacuamente. */
    Optional<Regra> findByInstrumentoProcedimentoConfigAndAtivoTrue(InstrumentoProcedimentoConfig instrumentoProcedimentoConfig);

    /** Ativa ou não: a `uq_regra_procedimento_config` cobre todas as linhas. */
    boolean existsByInstrumentoProcedimentoConfig_Id(Long instrumentoProcedimentoConfigId);

    /**
     * Filtros opcionais, combinados com E. O `LEFT JOIN` é explícito: o caminho implícito pelo Procedimento seria um
     * `INNER JOIN` e tiraria as regras de Dinâmica de todas as pesquisas.
     */
    @Query(value = "SELECT r FROM Regra r LEFT JOIN r.instrumentoProcedimentoConfig ipc "
            + "WHERE (:instrumentoDinamicaConfigId IS NULL OR r.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
            + "AND (:instrumentoProcedimentoConfigId IS NULL OR ipc.id = :instrumentoProcedimentoConfigId) "
            + "AND (:dinamicaDosProcedimentosId IS NULL OR ipc.instrumentoDinamicaConfig.id = :dinamicaDosProcedimentosId) "
            + "AND (:ativo IS NULL OR r.ativo = :ativo) "
            + "ORDER BY r.id",
            countQuery = "SELECT count(r) FROM Regra r LEFT JOIN r.instrumentoProcedimentoConfig ipc "
                    + "WHERE (:instrumentoDinamicaConfigId IS NULL OR r.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
                    + "AND (:instrumentoProcedimentoConfigId IS NULL OR ipc.id = :instrumentoProcedimentoConfigId) "
                    + "AND (:dinamicaDosProcedimentosId IS NULL OR ipc.instrumentoDinamicaConfig.id = :dinamicaDosProcedimentosId) "
                    + "AND (:ativo IS NULL OR r.ativo = :ativo)")
    Page<Regra> pesquisar(@Param("instrumentoDinamicaConfigId") Long instrumentoDinamicaConfigId,
                          @Param("instrumentoProcedimentoConfigId") Long instrumentoProcedimentoConfigId,
                          @Param("dinamicaDosProcedimentosId") Long dinamicaDosProcedimentosId,
                          @Param("ativo") Boolean ativo, Pageable pagina);
}
