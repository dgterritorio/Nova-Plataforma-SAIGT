package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificacaoDetalheResponse(UUID id, TipoNotificacao tipo, String titulo, String texto, UUID processoId,
                                         String numeroProcesso, String idIgtSnit, String descricaoIgt, Instant prazo,
                                         EstadoNotificacao estado, Instant criadoEm, Instant lidoEm,
                                         Instant resolvidoEm, String criadoPorNome, long numeroMensagens,
                                         long numeroDocumentos, PerfilInterno perfilDestino,
                                         String destinatarioUtilizadorNome, String destinatarioEntidadeNome,
                                         List<MensagemNotificacaoResponse> mensagens,
                                         List<DocumentoNotificacaoResponse> documentos,
                                         List<String> acoesDisponiveis) {

    public static NotificacaoDetalheResponse de(Notificacao notificacao, List<MensagemNotificacaoResponse> mensagens,
                                                List<DocumentoNotificacaoResponse> documentos,
                                                List<String> acoesDisponiveis) {
        NotificacaoResumoResponse resumo = NotificacaoResumoResponse.de(notificacao, mensagens.size(), documentos.size());
        return new NotificacaoDetalheResponse(resumo.id(), resumo.tipo(), resumo.titulo(), resumo.texto(),
                resumo.processoId(), resumo.numeroProcesso(), resumo.idIgtSnit(), resumo.descricaoIgt(),
                resumo.prazo(), resumo.estado(), resumo.criadoEm(), resumo.lidoEm(), resumo.resolvidoEm(),
                resumo.criadoPorNome(), resumo.numeroMensagens(), resumo.numeroDocumentos(),
                notificacao.getPerfilDestino(),
                notificacao.getUtilizador() == null ? null : notificacao.getUtilizador().getNome(),
                notificacao.getEntidade() == null ? null : notificacao.getEntidade().getDescricaoIncm(),
                mensagens, documentos, acoesDisponiveis);
    }
}
