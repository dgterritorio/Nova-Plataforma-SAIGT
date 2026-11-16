package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.TipoEntidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Só listagem e validação de owner — nenhuma verificação de autorização lê esta tabela. */
public interface UtilizadorEntidadeRepository extends JpaRepository<UtilizadorEntidade, UtilizadorEntidade.Id> {

    boolean existsByIdUtilizadorIdAndIdEntidadeId(UUID utilizadorId, Long entidadeId);

    boolean existsByIdEntidadeId(Long entidadeId);

    /** Vínculo do utilizador a uma entidade ativa do tipo dado (ex.: o destinatário de «Enviar PCM»). */
    boolean existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(UUID utilizadorId, TipoEntidade tipoEntidade);

    List<UtilizadorEntidade> findByIdEntidadeId(Long entidadeId);

    List<UtilizadorEntidade> findByIdUtilizadorId(UUID utilizadorId);

    @Query("SELECT ue.id.utilizadorId FROM UtilizadorEntidade ue WHERE ue.id.entidadeId = :entidadeId")
    List<UUID> findUtilizadorIdsByEntidadeId(@Param("entidadeId") Long entidadeId);

    @Query("SELECT ue.utilizador FROM UtilizadorEntidade ue WHERE ue.entidade.id = :entidadeId")
    List<Utilizador> findUtilizadoresByEntidadeId(@Param("entidadeId") Long entidadeId);

    /** Uma consulta por página; `entidade` vem já carregada para o nome e o `responsavel`. */
    @Query("SELECT ue FROM UtilizadorEntidade ue JOIN FETCH ue.entidade WHERE ue.id.utilizadorId IN :utilizadorIds")
    List<UtilizadorEntidade> findComEntidadeByUtilizadorIdIn(@Param("utilizadorIds") List<UUID> utilizadorIds);
}
