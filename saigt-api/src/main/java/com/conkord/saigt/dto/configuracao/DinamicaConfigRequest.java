package com.conkord.saigt.dto.configuracao;

import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import jakarta.validation.constraints.NotNull;

public record DinamicaConfigRequest(
        @NotNull Long tipoInstrumentoId,
        @NotNull Long tipoDinamicaId,
        @NotNull AssociacaoInstrumentos associacaoInstrumentos,
        @NotNull Boolean ativo) {}
