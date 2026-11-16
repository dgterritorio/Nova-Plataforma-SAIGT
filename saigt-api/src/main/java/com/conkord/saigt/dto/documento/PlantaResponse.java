package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.Processo;

import java.time.Instant;
import java.util.UUID;

public record PlantaResponse(
        UUID id,
        UUID processoId,
        String numeroProcesso,
        String numeroDeposito,
        String idus,
        UUID processoDocumentoId,
        String nomeRegulamento,
        String numeroFolha,
        String nomeTiff,
        String nomeZip,
        String linkJpeg,
        boolean emVigor,
        Instant atualizadoEm,
        String atualizadoPorNome
) {

    public static PlantaResponse de(Planta planta, String idus) {
        Processo processo = planta.getProcesso();
        return new PlantaResponse(planta.getId(), processo.getId(), processo.getNumeroProcesso(),
                processo.getNumeroDeposito(), idus, planta.getProcessoDocumento().getId(), planta.getNomeRegulamento(),
                planta.getNumeroFolha(), planta.getNomeTiff(), planta.getNomeZip(), planta.getLinkJpeg(),
                planta.isEmVigor(), planta.getAtualizadoEm(),
                planta.getAtualizadoPor() == null ? null : planta.getAtualizadoPor().getNome());
    }
}
