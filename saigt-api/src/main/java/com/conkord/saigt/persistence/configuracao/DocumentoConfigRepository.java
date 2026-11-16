package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface DocumentoConfigRepository extends JpaRepository<DocumentoConfig, Long> {

    /** Em lote, evita N+1. */
    List<DocumentoConfig> findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(
            List<InstrumentoProcedimentoConfig> instrumentoProcedimentoConfigs);

    List<DocumentoConfig> findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(
            InstrumentoProcedimentoConfig instrumentoProcedimentoConfig);

    @Query("select distinct d.elementoInformacaoBase from DocumentoConfig d "
            + "where d.instrumentoProcedimentoConfig = :config and d.ativo = true "
            + "and d.elementoInformacaoBase is not null")
    Set<ElementoInformacaoBase> findElementosInformacaoBaseAtivos(
            @Param("config") InstrumentoProcedimentoConfig config);

    boolean existsByInstrumentoProcedimentoConfig_IdAndCodigo(Long instrumentoProcedimentoConfigId, String codigo);

    Page<DocumentoConfig> findByInstrumentoProcedimentoConfig_Id(Long instrumentoProcedimentoConfigId, Pageable pagina);

    Page<DocumentoConfig> findByInstrumentoProcedimentoConfig_IdAndAtivo(Long instrumentoProcedimentoConfigId,
                                                                        Boolean ativo, Pageable pagina);

    List<DocumentoConfig> findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(
            Long instrumentoProcedimentoConfigId, String separador);
}
