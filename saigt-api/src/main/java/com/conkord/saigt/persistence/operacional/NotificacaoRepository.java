package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID>, JpaSpecificationExecutor<Notificacao> {

    String ESTADO_JPQL = "com.conkord.saigt.persistence.operacional.EstadoNotificacao.";

    List<Notificacao> findByLoteId(UUID loteId);

    boolean existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(UUID processoId,
                                                                                    TipoNotificacao tipo,
                                                                                    Instant desde);

    boolean existsByProcesso_IdAndTitulo(UUID processoId, String titulo);

    boolean existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(TipoNotificacao tipo,
                                                                            EstadoNotificacao estado,
                                                                            PerfilInterno perfilDestino,
                                                                            String texto);

    /** Com os destinos já carregados, para resolver os endereços da cópia por email sem N+1. */
    @Query("SELECT n FROM Notificacao n LEFT JOIN FETCH n.processo LEFT JOIN FETCH n.utilizador "
            + "LEFT JOIN FETCH n.entidade WHERE n.id IN :ids")
    List<Notificacao> findComDestinosByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.estado = " + ESTADO_JPQL + "LIDA, n.lidoEm = :agora, n.atualizadoEm = :agora "
            + "WHERE n.id = :id AND n.estado = " + ESTADO_JPQL + "ATIVA")
    int marcarLida(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.estado = " + ESTADO_JPQL + "RESOLVIDA, n.resolvidoEm = :agora, "
            + "n.atualizadoEm = :agora WHERE n.id = :id AND n.estado = " + ESTADO_JPQL + "ATIVA")
    int marcarResolvida(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.estado = " + ESTADO_JPQL + "REMOVIDA, n.atualizadoEm = :agora "
            + "WHERE n.id = :id AND n.estado = " + ESTADO_JPQL + "ATIVA")
    int marcarRemovida(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.estado = " + ESTADO_JPQL + "REMOVIDA, n.atualizadoEm = :agora "
            + "WHERE n.loteId = :loteId AND n.estado = " + ESTADO_JPQL + "ATIVA")
    int marcarRemovidasDoLote(@Param("loteId") UUID loteId, @Param("agora") Instant agora);
    
    boolean existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTitulo(TipoNotificacao tipo,
                                                                             EstadoNotificacao estado,
                                                                             PerfilInterno perfilDestino,
                                                                             String titulo);

    /** Para duas respostas simultâneas não resolverem a mesma notificação duas vezes. Exige transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notificacao n WHERE n.id = :id")
    Optional<Notificacao> findComBloqueioById(@Param("id") UUID id);
}
