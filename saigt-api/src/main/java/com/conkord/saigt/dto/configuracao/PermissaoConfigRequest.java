package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.core.TipoEntidade;
import jakarta.validation.constraints.NotNull;

public record PermissaoConfigRequest(
        @NotNull Long instrumentoProcedimentoConfigId,
        @NotNull TipoEntidade tipoEntidade,
        @NotNull Boolean ativo) {}
