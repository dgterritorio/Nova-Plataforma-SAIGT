package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoProcedimento;

public record PermissaoConfigResponse(Long id, Long instrumentoProcedimentoConfigId, TipoResumo tipoProcedimento,
                                      TipoEntidade tipoEntidade, boolean ativo) {

    public static PermissaoConfigResponse de(PermissaoAcao permissao) {
        TipoProcedimento tipo = permissao.getInstrumentoProcedimentoConfig().getTipoProcedimento();
        return new PermissaoConfigResponse(permissao.getId(), permissao.getInstrumentoProcedimentoConfig().getId(),
                new TipoResumo(tipo.getId(), tipo.getCodigo(), tipo.getNome()), permissao.getTipoEntidade(),
                permissao.isAtivo());
    }
}
