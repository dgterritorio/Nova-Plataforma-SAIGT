package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Uma submissão do Procedimento no histórico documental, com os seus ficheiros. */
public record ProcessoHistoricoDocumentalResponse(
        UUID processoId,
        String numeroProcesso,
        int ocorrencia,
        EstadoProcesso estado,
        Instant dataSubmissao,
        String numeroDr,
        LocalDate dataPublicacaoDr,
        String linkDr,
        boolean atual,
        List<ProcessoDocumentoListagemResponse> documentos
) {
    public static ProcessoHistoricoDocumentalResponse de(Processo processo, boolean atual,
                                                         List<ProcessoDocumento> documentos) {
        return new ProcessoHistoricoDocumentalResponse(
                processo.getId(),
                processo.getNumeroProcesso(),
                processo.getOcorrencia(),
                processo.getEstado(),
                processo.getDataSubmissao(),
                processo.getNumeroDr(),
                processo.getDataPublicacaoDr(),
                processo.getLinkDr(),
                atual,
                documentos.stream().map(ProcessoDocumentoListagemResponse::de).toList());
    }
}
