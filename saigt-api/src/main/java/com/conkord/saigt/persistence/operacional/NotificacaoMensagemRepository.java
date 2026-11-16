package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificacaoMensagemRepository extends JpaRepository<NotificacaoMensagem, UUID> {

    List<NotificacaoMensagem> findByNotificacao_IdOrderByCriadoEmAsc(UUID notificacaoId);

    @Query("SELECT m.notificacao.id AS notificacaoId, COUNT(m) AS total FROM NotificacaoMensagem m "
            + "WHERE m.notificacao.id IN :ids GROUP BY m.notificacao.id")
    List<ContagemPorNotificacao> contarPorNotificacao(@Param("ids") Collection<UUID> ids);
}
