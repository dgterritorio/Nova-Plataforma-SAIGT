package com.conkord.saigt.persistence.core;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntidadeRepository extends JpaRepository<Entidade, Long>, JpaSpecificationExecutor<Entidade> {

    List<Entidade> findAllByNipc(String nipc);

    /**
     * Provisório: o NIPC já não é único, por isso devolve vazio quando corresponde a mais de uma entidade.
     * Sai quando o login e a gestão de entidades deixarem de procurar por NIPC.
     */
    @Deprecated
    default Optional<Entidade> findByNipc(String nipc) {
        List<Entidade> entidades = findAllByNipc(nipc);
        return entidades.size() == 1 ? Optional.of(entidades.get(0)) : Optional.empty();
    }

    /** `descricaoIncm` não é única: serve para detetar divergência nipc/nome no login. */
    List<Entidade> findAllByDescricaoIncm(String descricaoIncm);

    boolean existsByDescricaoIncmIgnoreCaseAndAtivoTrue(String descricaoIncm);

    Optional<Entidade> findByCodigo(String codigo);

    List<Entidade> findByTipoEntidadeAndDicoIsNotNullAndAtivoTrue(TipoEntidade tipoEntidade);

    /** A lista das entidades emitentes de um tipo (formulário da Suspensão da CCDR). */
    List<Entidade> findByTipoEntidadeAndAtivoTrueOrderByDescricaoSaigtAscIdAsc(TipoEntidade tipoEntidade);

    boolean existsByTipoEntidadeAndDescricaoSaigtAndAtivoTrue(TipoEntidade tipoEntidade, String descricaoSaigt);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    @Query("SELECT e FROM Entidade e WHERE e.dico IS NOT NULL AND NOT EXISTS (SELECT r FROM SnitReferencia r "
            + "WHERE r.lista = :lista AND r.codigo = e.dico AND r.ativo = true) ORDER BY e.codigo")
    List<Entidade> findAllComDicoSemReferenciaAtiva(@Param("lista") ListaReferenciaSnit lista);
}
