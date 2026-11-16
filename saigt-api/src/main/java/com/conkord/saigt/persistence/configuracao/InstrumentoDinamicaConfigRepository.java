package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoInstrumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstrumentoDinamicaConfigRepository extends JpaRepository<InstrumentoDinamicaConfig, Long> {

    List<InstrumentoDinamicaConfig> findByTipoInstrumentoAndAtivoTrue(TipoInstrumento tipoInstrumento);

    /** Um tipo sem Dinâmica configurada não é operável. Filtra por configuração, nunca por ator. */
    @Query("SELECT DISTINCT c.tipoInstrumento FROM InstrumentoDinamicaConfig c "
            + "WHERE c.ativo = true AND c.tipoInstrumento.ativo = true "
            + "ORDER BY c.tipoInstrumento.codigo")
    List<TipoInstrumento> listarTiposInstrumentoConfigurados();

    /** Os tipos de Instrumento que uma Dinâmica deste tipo admite: os das suas configurações ativas. */
    @Query("SELECT c.tipoInstrumento FROM InstrumentoDinamicaConfig c "
            + "WHERE c.tipoDinamica.id = :tipoDinamicaId AND c.ativo = true AND c.tipoInstrumento.ativo = true "
            + "ORDER BY c.tipoInstrumento.codigo")
    List<TipoInstrumento> listarTiposInstrumentoAdmitidos(@Param("tipoDinamicaId") Long tipoDinamicaId);

    /**
     * Inclui inativas; agrupadas por tipo de instrumento e, dentro dele, pela ordem do catálogo de tipos de Dinâmica.
     * `JOIN FETCH` evita um `SELECT` por linha.
     */
    @Query("SELECT c FROM InstrumentoDinamicaConfig c "
            + "JOIN FETCH c.tipoInstrumento JOIN FETCH c.tipoDinamica "
            + "ORDER BY c.tipoInstrumento.id, c.tipoDinamica.id")
    List<InstrumentoDinamicaConfig> listarTodosOrdenados();

    boolean existsByTipoInstrumento_Id(Long tipoInstrumentoId);

    boolean existsByTipoDinamica_Id(Long tipoDinamicaId);

    boolean existsByTipoInstrumento_IdAndTipoDinamica_Id(Long tipoInstrumentoId, Long tipoDinamicaId);

    boolean existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(Long tipoInstrumentoId, Long tipoDinamicaId, Long id);

    /** Filtros opcionais; dentro do tipo de Instrumento, pela ordem do catálogo de tipos de Dinâmica. */
    @Query(value = "SELECT c FROM InstrumentoDinamicaConfig c "
            + "JOIN FETCH c.tipoInstrumento ti JOIN FETCH c.tipoDinamica td "
            + "WHERE (:tipoInstrumentoId IS NULL OR ti.id = :tipoInstrumentoId) "
            + "AND (:ativo IS NULL OR c.ativo = :ativo) "
            + "ORDER BY ti.codigo, td.id",
            countQuery = "SELECT count(c) FROM InstrumentoDinamicaConfig c "
                    + "WHERE (:tipoInstrumentoId IS NULL OR c.tipoInstrumento.id = :tipoInstrumentoId) "
                    + "AND (:ativo IS NULL OR c.ativo = :ativo)")
    Page<InstrumentoDinamicaConfig> pesquisar(@Param("tipoInstrumentoId") Long tipoInstrumentoId,
                                               @Param("ativo") Boolean ativo, Pageable pagina);
}
