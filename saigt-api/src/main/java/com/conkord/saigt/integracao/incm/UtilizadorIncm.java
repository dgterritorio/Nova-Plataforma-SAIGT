package com.conkord.saigt.integracao.incm;

import java.util.List;

/**
 * Resultado de `ValidateUser`; `adesoes` é `0..x`.
 * `cargo` só existe para utilizadores externos: a INCM devolve-o, o AD da DGT não.
 */
public record UtilizadorIncm(String nome, String email, String cargo, List<AdesaoIncm> adesoes) {
}
