package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;

import java.time.Instant;
import java.util.UUID;

public record NotificacaoResumoResponse(UUID id, TipoNotificacao tipo, String titulo, String texto, UUID processoId,
                                        String numeroProcesso, String idIgtSnit, String descricaoIgt, Instant prazo,
                                        EstadoNotificacao estado, Instant criadoEm, Instant lidoEm,
                                        Instant resolvidoEm, String criadoPorNome, long numeroMensagens,
                                        long numeroDocumentos) {

    public static NotificacaoResumoResponse de(Notificacao notificacao, long numeroMensagens, long numeroDocumentos) {
        return new NotificacaoResumoResponse(notificacao.getId(), notificacao.getTipo(), notificacao.getTitulo(),
                notificacao.getTexto(),
                notificacao.getProcesso() == null ? null : notificacao.getProcesso().getId(),
                notificacao.getProcesso() == null ? null : notificacao.getProcesso().getNumeroProcesso(),
                notificacao.getIdIgtSnit(), notificacao.getDescricaoIgt(), notificacao.getPrazo(),
                notificacao.getEstado(), notificacao.getCriadoEm(), notificacao.getLidoEm(),
                notificacao.getResolvidoEm(),
                notificacao.getCriadoPor() == null ? null : notificacao.getCriadoPor().getNome(),
                numeroMensagens, numeroDocumentos);
    }
}
