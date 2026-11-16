package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacaoDocumentoRepository extends JpaRepository<NotificacaoDocumento, UUID> {

    List<NotificacaoDocumento> findByNotificacao_IdOrderByCriadoEmAsc(UUID notificacaoId);

    Optional<NotificacaoDocumento> findByIdAndNotificacao_Id(UUID id, UUID notificacaoId);

    @Query("select d.caminhoFicheiro from NotificacaoDocumento d where d.caminhoFicheiro in :caminhos")
    List<String> findCaminhosExistentes(@Param("caminhos") Collection<String> caminhos);

    @Query("SELECT d.notificacao.id AS notificacaoId, COUNT(d) AS total FROM NotificacaoDocumento d "
            + "WHERE d.notificacao.id IN :ids GROUP BY d.notificacao.id")
    List<ContagemPorNotificacao> contarPorNotificacao(@Param("ids") Collection<UUID> ids);
}
