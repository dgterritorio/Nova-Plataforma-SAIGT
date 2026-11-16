package com.conkord.saigt.persistence.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Só criação e leitura: a auditoria é imutável. */
public interface AuditoriaRepository extends Repository<Auditoria, Long> {

    Auditoria save(Auditoria auditoria);

    List<Auditoria> findByTipoOcorrenciaAndIdOcorrencia(String tipoOcorrencia, String idOcorrencia);

    List<Auditoria> findByCriadoEmBetween(Instant inicio, Instant fim);

    /** Desempate por `id`: várias transições podem partilhar o mesmo `criado_em` (resolução ao milissegundo). */
    Page<Auditoria> findByTipoOcorrenciaAndIdOcorrenciaOrderByCriadoEmDescIdDesc(
            String tipoOcorrencia, String idOcorrencia, Pageable pageable);

    /** Mesmo desempate por `id`. */
    Optional<Auditoria> findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
            String tipoOcorrencia, String idOcorrencia, String acao);

    /** Mesmo desempate por `id`. */
    Optional<Auditoria> findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
            String tipoOcorrencia, String idOcorrencia, Collection<String> acoes);

    List<Auditoria> findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
            String tipoOcorrencia, String idOcorrencia, String acao);

    long countByTipoOcorrenciaAndIdOcorrenciaAndAcao(String tipoOcorrencia, String idOcorrencia, String acao);

    /** Ato praticado numa sessão da entidade; a auditoria da própria gestão da entidade não conta. */
    @Query("SELECT COUNT(a) > 0 FROM Auditoria a WHERE a.entidadeId = :entidadeId "
            + "AND NOT (a.tipoOcorrencia = 'ENTIDADE' AND a.acao IN ('CRIAR', 'ATUALIZAR'))")
    boolean existsByEntidadeId(@Param("entidadeId") Long entidadeId);

    /** Um utilizador que já praticou algum ato nunca é removido. */
    boolean existsByUtilizadorId(UUID utilizadorId);

    /** Roster de quem atuou pela entidade desde a data, via {@code idx_auditoria_entidade}. */
    @Query("SELECT DISTINCT a.utilizadorId FROM Auditoria a WHERE a.entidadeId = :entidadeId "
            + "AND a.criadoEm >= :desde AND a.utilizadorId IS NOT NULL")
    List<UUID> findUtilizadorIdsQueAtuaramNaEntidadeDesde(@Param("entidadeId") Long entidadeId, @Param("desde") Instant desde);

    /** Uma consulta por página, via {@code idx_auditoria_entidade}; quem nunca atuou pela entidade não vem. */
    @Query("SELECT a.utilizadorId AS utilizadorId, MAX(a.criadoEm) AS ultimaAtuacaoEm FROM Auditoria a "
            + "WHERE a.entidadeId = :entidadeId AND a.utilizadorId IN :utilizadorIds GROUP BY a.utilizadorId")
    List<UltimaAtuacaoProjecao> findUltimaAtuacaoNaEntidadeEmLote(@Param("entidadeId") Long entidadeId,
                                                                   @Param("utilizadorIds") List<UUID> utilizadorIds);

    interface UltimaAtuacaoProjecao {
        UUID getUtilizadorId();

        Instant getUltimaAtuacaoEm();
    }

    List<Auditoria> findTop10ByOrderByCriadoEmDescIdDesc();

    /**
     * `LOGIN` é registado pelos dois fluxos de autenticação, por isso serve qualquer origem.
     * Uma consulta por página, via {@code idx_auditoria_utilizador_entidade}.
     */
    @Query("SELECT a.utilizadorId AS utilizadorId, MAX(a.criadoEm) AS ultimoLoginEm FROM Auditoria a "
            + "WHERE a.utilizadorId IN :utilizadorIds AND a.tipoOcorrencia = 'UTILIZADOR' AND a.acao = 'LOGIN' "
            + "GROUP BY a.utilizadorId")
    List<UltimoLoginProjecao> findUltimoLoginEmLotePorUtilizador(@Param("utilizadorIds") List<UUID> utilizadorIds);

    interface UltimoLoginProjecao {
        UUID getUtilizadorId();

        Instant getUltimoLoginEm();
    }

    /** Uma consulta para todas as ocorrências; as que não têm transições não vêm na lista. */
    @Query("SELECT a.idOcorrencia AS idOcorrencia, COUNT(a) AS total FROM Auditoria a "
            + "WHERE a.tipoOcorrencia = :tipoOcorrencia AND a.idOcorrencia IN :idsOcorrencia "
            + "AND a.estadoDepois = :estadoDepois GROUP BY a.idOcorrencia")
    List<TransicoesPorOcorrenciaProjecao> contarTransicoesPorOcorrencia(@Param("tipoOcorrencia") String tipoOcorrencia,
                                                                        @Param("idsOcorrencia") Collection<String> idsOcorrencia,
                                                                        @Param("estadoDepois") String estadoDepois);

    interface TransicoesPorOcorrenciaProjecao {
        String getIdOcorrencia();

        long getTotal();
    }
}
