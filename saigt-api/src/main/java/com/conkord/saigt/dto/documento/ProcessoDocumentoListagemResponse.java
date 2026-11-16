package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Linha do separador Documentos. Sem `caminhoFicheiro`: o caminho no bucket nunca sai do backend. */
public record ProcessoDocumentoListagemResponse(
        UUID id,
        Long documentoConfigId,
        String nomeFicheiro,
        CategoriaDocumento categoria,
        String extensao,
        OrigemDocumento origem,
        boolean bloqueadoEdicao,
        EstadoValidacao estadoValidacao,
        long tamanhoBytes,
        Instant criadoEm,
        UUID criadoPorId,
        String criadoPorNome,
        // Os itens de depósito obrigatório que o ficheiro também satisfaz, por `documentoConfigId` crescente.
        List<Long> tags,
        String justificacaoDesbloqueio
) {
    public static ProcessoDocumentoListagemResponse de(ProcessoDocumento documento) {
        return new ProcessoDocumentoListagemResponse(
                documento.getId(),
                documento.getDocumentoConfig() == null ? null : documento.getDocumentoConfig().getId(),
                documento.getNomeFicheiro(),
                documento.getCategoria(),
                documento.getExtensao(),
                documento.getOrigem(),
                documento.isBloqueadoEdicao(),
                documento.getEstadoValidacao(),
                documento.getTamanhoBytes(),
                documento.getCriadoEm(),
                documento.getCriadoPor().getId(),
                documento.getCriadoPor().getNome(),
                documento.idsDasTags(),
                documento.getJustificacaoDesbloqueio());
    }
}
