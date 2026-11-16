package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sem `caminhoFicheiro`: o caminho no bucket nunca sai do backend. */
public record ProcessoDocumentoResponse(
        UUID id,
        UUID processoId,
        Long documentoConfigId,
        CategoriaDocumento categoria,
        String extensao,
        OrigemDocumento origem,
        boolean bloqueadoEdicao,
        EstadoValidacao estadoValidacao,
        String nomeFicheiro,
        long tamanhoBytes,
        String hashSha256,
        UUID criadoPorId,
        Instant criadoEm,
        List<Long> tags,
        String justificacaoDesbloqueio
) {
    public static ProcessoDocumentoResponse de(ProcessoDocumento documento) {
        return new ProcessoDocumentoResponse(
                documento.getId(),
                documento.getProcesso().getId(),
                documento.getDocumentoConfig() == null ? null : documento.getDocumentoConfig().getId(),
                documento.getCategoria(),
                documento.getExtensao(),
                documento.getOrigem(),
                documento.isBloqueadoEdicao(),
                documento.getEstadoValidacao(),
                documento.getNomeFicheiro(),
                documento.getTamanhoBytes(),
                documento.getHashSha256(),
                documento.getCriadoPor().getId(),
                documento.getCriadoEm(),
                documento.idsDasTags(),
                documento.getJustificacaoDesbloqueio());
    }
}
