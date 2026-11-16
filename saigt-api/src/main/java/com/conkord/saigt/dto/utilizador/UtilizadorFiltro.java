package com.conkord.saigt.dto.utilizador;

import java.time.LocalDate;

/**
 * Nulo = sem filtro nesse campo. `entidadeId` sozinho resolve-se sobre `utilizador_entidade`; com `atuouDesde`, sobre
 * `auditoria` dessa entidade. `atuouDesde` exige `entidadeId`.
 */
public record UtilizadorFiltro(String origemAutenticacao, String perfilInterno, Long entidadeId, LocalDate atuouDesde) {
}
