package com.conkord.saigt.dto.workflow;

import java.util.List;

/** Passo (`instrumento_procedimento_config`). `prazoDias`/`tipoPrazo` nulos quando não há prazo configurado. */
public record PassoResponse(String nome, int ordem, boolean obrigatorio, String serieDr, Integer prazoDias,
                             String tipoPrazo, List<String> atores) {
}
