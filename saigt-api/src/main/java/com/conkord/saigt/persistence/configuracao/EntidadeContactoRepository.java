package com.conkord.saigt.persistence.configuracao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntidadeContactoRepository extends JpaRepository<EntidadeContacto, Long> {

    List<EntidadeContacto> findAllByEntidadeIdAndAtivoTrue(Long entidadeId);

    /** Ativos e inativos. */
    boolean existsByEntidadeId(Long entidadeId);

    /** Ativos e inativos, o principal primeiro e os inativos no fim. */
    List<EntidadeContacto> findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(Long entidadeId);

    /** No máximo um, garantido pelo índice `uq_entidade_contacto_principal_ativo`. */
    Optional<EntidadeContacto> findByEntidadeIdAndPrincipalTrueAndAtivoTrue(Long entidadeId);

    /** Em lote, evita N+1. */
    List<EntidadeContacto> findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(List<Long> entidadeIds);
}
