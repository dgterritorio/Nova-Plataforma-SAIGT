package com.conkord.saigt.dto.workflow;

import com.conkord.saigt.dto.instrumento.TipoInstrumentoResponse;

import java.util.List;

/** Workflow (`instrumento_dinamica_config`), só leitura; a configuração entra por seed. */
public record WorkflowResponse(Long id, String titulo, TipoInstrumentoResponse tipoInstrumento, boolean ativo, int numeroPassos, long processosAtivos,
                                List<PassoResponse> passos) {
}
