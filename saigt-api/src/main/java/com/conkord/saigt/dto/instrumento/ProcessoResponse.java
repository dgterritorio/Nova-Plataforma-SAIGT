package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.instrumento.CamposFormularioInicial;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessoResponse(
        UUID id,
        String numeroProcesso,
        UUID dinamicaId,
        Long instrumentoProcedimentoConfigId,
        String estado,
        int ocorrencia,
        long versao,
        String origemDados,
        Long tipoAtoId,
        UUID criadoPorId,
        String dadosFormulario,
        Instant dataSubmissao,
        UUID pcmAtribuidoAId,
        /** Última devolução à DGT pela PCM; nula se o processo nunca foi devolvido nesta passagem por `VALIDADO_DGT`. */
        Instant pcmDevolvidoEm,
        List<String> camposBloqueados,
        /** O ato publicado que um ato pontual visa; nulo fora dos Procedimentos `ato_pontual`. */
        UUID processoReferidoId
) {
    public static ProcessoResponse de(Processo processo) {
        return new ProcessoResponse(
                processo.getId(),
                processo.getNumeroProcesso(),
                processo.getDinamica().getId(),
                processo.getInstrumentoProcedimentoConfig().getId(),
                processo.getEstado().name(),
                processo.getOcorrencia(),
                processo.getVersao(),
                processo.getOrigemDados(),
                processo.getTipoAto() == null ? null : processo.getTipoAto().getId(),
                processo.getCriadoPor().getId(),
                processo.getDadosFormulario(),
                processo.getDataSubmissao(),
                processo.getPcmAtribuidoA() == null ? null : processo.getPcmAtribuidoA().getId(),
                processo.getPcmDevolvidoEm(),
                CamposFormularioInicial.camposBloqueados(processo),
                processo.getProcessoReferido() == null ? null : processo.getProcessoReferido().getId());
    }
}
