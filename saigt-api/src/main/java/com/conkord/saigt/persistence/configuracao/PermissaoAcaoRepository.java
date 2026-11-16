package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PermissaoAcaoRepository extends JpaRepository<PermissaoAcao, Long> {

    boolean existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(
            TipoEntidade tipoEntidade, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig);

    /**
     * O tipo de entidade tem linha ativa no Procedimento inicial (o ativo de menor `ordem`) de pelo menos uma
     * configuração de Dinâmica ativa do tipo de Instrumento: é o que permite criar um Instrumento desse tipo. As
     * Dinâmicas que se iniciam sem IGT não contam: não precisam de criar o Instrumento antes.
     */
    @Query("SELECT CASE WHEN COUNT(pa) > 0 THEN true ELSE false END FROM PermissaoAcao pa "
            + "JOIN pa.instrumentoProcedimentoConfig ipc JOIN ipc.instrumentoDinamicaConfig idc "
            + "WHERE pa.tipoEntidade = :tipoEntidade AND pa.ativo = true AND ipc.ativo = true "
            + "AND idc.ativo = true AND idc.tipoInstrumento = :tipoInstrumento "
            + "AND idc.tipoDinamica.inicioSemIgt = false "
            + "AND ipc.ordem = (SELECT MIN(inicial.ordem) FROM InstrumentoProcedimentoConfig inicial "
            + "WHERE inicial.instrumentoDinamicaConfig = idc AND inicial.ativo = true)")
    boolean existeNoProcedimentoInicialDoTipo(@Param("tipoEntidade") TipoEntidade tipoEntidade,
                                              @Param("tipoInstrumento") TipoInstrumento tipoInstrumento);

    /** Só referência para o FE, nunca fonte de autorização. */
    List<PermissaoAcao> findAllByTipoEntidadeAndAtivoTrue(TipoEntidade tipoEntidade);

    /** Em lote, evita N+1. */
    List<PermissaoAcao> findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(
            Collection<Long> instrumentoProcedimentoConfigIds);

    boolean existsByInstrumentoProcedimentoConfig_IdAndTipoEntidade(Long instrumentoProcedimentoConfigId,
                                                                    TipoEntidade tipoEntidade);

    boolean existsByInstrumentoProcedimentoConfig_IdAndTipoEntidadeAndIdNot(Long instrumentoProcedimentoConfigId,
                                                                            TipoEntidade tipoEntidade, Long id);

    @Query(value = "SELECT pa FROM PermissaoAcao pa JOIN FETCH pa.instrumentoProcedimentoConfig ipc "
            + "JOIN FETCH ipc.tipoProcedimento "
            + "WHERE (:instrumentoDinamicaConfigId IS NULL OR ipc.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
            + "AND (:instrumentoProcedimentoConfigId IS NULL OR ipc.id = :instrumentoProcedimentoConfigId) "
            + "AND (:tipoEntidade IS NULL OR pa.tipoEntidade = :tipoEntidade) "
            + "AND (:ativo IS NULL OR pa.ativo = :ativo) "
            + "ORDER BY ipc.ordem, ipc.id, pa.tipoEntidade, pa.id",
            countQuery = "SELECT count(pa) FROM PermissaoAcao pa JOIN pa.instrumentoProcedimentoConfig ipc "
                    + "WHERE (:instrumentoDinamicaConfigId IS NULL OR ipc.instrumentoDinamicaConfig.id = :instrumentoDinamicaConfigId) "
                    + "AND (:instrumentoProcedimentoConfigId IS NULL OR ipc.id = :instrumentoProcedimentoConfigId) "
                    + "AND (:tipoEntidade IS NULL OR pa.tipoEntidade = :tipoEntidade) "
                    + "AND (:ativo IS NULL OR pa.ativo = :ativo)")
    Page<PermissaoAcao> pesquisar(@Param("instrumentoDinamicaConfigId") Long instrumentoDinamicaConfigId,
                                  @Param("instrumentoProcedimentoConfigId") Long instrumentoProcedimentoConfigId,
                                  @Param("tipoEntidade") TipoEntidade tipoEntidade,
                                  @Param("ativo") Boolean ativo, Pageable pagina);
}
