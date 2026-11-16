package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;

/**
 * {@code formulario}: o formulário fixo do tipo de Procedimento, ou nulo quando não há. {@code emUso}: já existe pelo
 * menos um processo desta configuração, por isso a chave não muda.
 */
public record ProcedimentoConfigResponse(Long id, Long instrumentoDinamicaConfigId, TipoResumo tipoProcedimento,
                                         int ordem, boolean obrigatorio, String serieDr, Long tamanhoMaximoProcesso,
                                         boolean repetivel, boolean atoPontual, boolean ativo, String formulario,
                                         boolean emUso, boolean visivelNaDinamica) {

    public static ProcedimentoConfigResponse de(InstrumentoProcedimentoConfig config, String formulario, boolean emUso) {
        return new ProcedimentoConfigResponse(config.getId(), config.getInstrumentoDinamicaConfig().getId(),
                new TipoResumo(config.getTipoProcedimento().getId(), config.getTipoProcedimento().getCodigo(),
                        config.getTipoProcedimento().getNome()),
                config.getOrdem(), config.isObrigatorio(), config.getSerieDr(), config.getTamanhoMaximoProcesso(),
                config.isRepetivel(), config.isAtoPontual(), config.isAtivo(), formulario, emUso,
                config.isVisivelNaDinamica());
    }
}
