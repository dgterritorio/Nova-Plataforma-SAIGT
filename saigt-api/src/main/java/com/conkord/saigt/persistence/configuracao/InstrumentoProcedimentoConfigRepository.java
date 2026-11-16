package com.conkord.saigt.persistence.configuracao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InstrumentoProcedimentoConfigRepository extends JpaRepository<InstrumentoProcedimentoConfig, Long> {

    /** Sequenciamento por omissão: os Procedimentos opcionais nunca bloqueiam os seguintes. */
    List<InstrumentoProcedimentoConfig> findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(
            InstrumentoDinamicaConfig instrumentoDinamicaConfig, int ordem);

    List<InstrumentoProcedimentoConfig> findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(
            InstrumentoDinamicaConfig instrumentoDinamicaConfig);

    /** O Procedimento inicial: o ativo de menor `ordem`, o que inicia a Dinâmica (API-PROC-004; FR-161, FR-179). */
    Optional<InstrumentoProcedimentoConfig> findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(
            InstrumentoDinamicaConfig instrumentoDinamicaConfig);

    /** O Procedimento final: os opcionais nunca bloqueiam (TR-100), por isso nunca são o último. */
    Optional<InstrumentoProcedimentoConfig> findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(
            InstrumentoDinamicaConfig instrumentoDinamicaConfig);

    /** O Procedimento que termina a Dinâmica: o final, sem os atos pontuais, que não são Procedimentos dela (BL-PROC-003). */
    Optional<InstrumentoProcedimentoConfig> findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc(
            InstrumentoDinamicaConfig instrumentoDinamicaConfig);

    /** Em lote, evita N+1. `JOIN FETCH` evita um `SELECT` por linha para `tipoProcedimento`. */
    @Query("SELECT p FROM InstrumentoProcedimentoConfig p JOIN FETCH p.tipoProcedimento "
            + "WHERE p.instrumentoDinamicaConfig.id IN :configIds AND p.ativo = true "
            + "ORDER BY p.instrumentoDinamicaConfig.id, p.ordem")
    List<InstrumentoProcedimentoConfig> listarAtivosPorConfigs(@Param("configIds") Collection<Long> configIds);

    boolean existsByTipoProcedimento_Id(Long tipoProcedimentoId);

    boolean existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_Id(Long instrumentoDinamicaConfigId,
                                                                       Long tipoProcedimentoId);

    boolean existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_IdAndIdNot(Long instrumentoDinamicaConfigId,
                                                                               Long tipoProcedimentoId, Long id);

    @Query(value = "SELECT p FROM InstrumentoProcedimentoConfig p JOIN FETCH p.tipoProcedimento "
            + "WHERE (:instrumentoDinamicaConfigId IS NULL OR p.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
            + "AND (:ativo IS NULL OR p.ativo = :ativo) "
            + "ORDER BY p.instrumentoDinamicaConfig.id, p.ordem, p.id",
            countQuery = "SELECT count(p) FROM InstrumentoProcedimentoConfig p "
                    + "WHERE (:instrumentoDinamicaConfigId IS NULL OR p.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
                    + "AND (:ativo IS NULL OR p.ativo = :ativo)")
    Page<InstrumentoProcedimentoConfig> pesquisar(@Param("instrumentoDinamicaConfigId") Long instrumentoDinamicaConfigId,
                                                   @Param("ativo") Boolean ativo, Pageable pagina);
}
