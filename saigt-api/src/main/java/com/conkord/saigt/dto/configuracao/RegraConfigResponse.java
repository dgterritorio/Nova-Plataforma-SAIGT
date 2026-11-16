package com.conkord.saigt.dto.configuracao;

import java.util.List;

/** `condicoes` ativas e inativas, por `grupo` e `id`. */
public record RegraConfigResponse(Long id, Long instrumentoDinamicaConfigId, Long instrumentoProcedimentoConfigId,
                                  boolean substituiSequenciamento, String operador, String descricao, boolean ativo,
                                  List<CondicaoConfigResponse> condicoes) {}
