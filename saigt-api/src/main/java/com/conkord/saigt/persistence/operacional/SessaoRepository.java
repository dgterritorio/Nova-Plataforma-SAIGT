package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, String> {

    /** No máximo uma ATIVA por utilizador. Filtra por `tipo` para nunca revogar uma PRE_AUTENTICACAO em curso. */
    List<Sessao> findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(UUID utilizadorId, String tipo);

    /** Para consumir uma pré-autenticação uma só vez: o segundo pedido espera e vê a linha já revogada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sessao s where s.jti = :jti")
    Optional<Sessao> findByJtiComBloqueio(@Param("jti") String jti);

    /** Hard delete: `sessao` não tem valor de auditoria. Inclui `PRE_AUTENTICACAO` abandonada. */
    long deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant instante);
}
