package com.conkord.saigt.dto.utilizador;

/**
 * Vínculo de `utilizador_entidade`. `responsavel`: a pessoa é o responsável do termo de adesão
 * (`entidade.utilizador_email` igual ao email da pessoa).
 */
public record EntidadeVinculoResponse(Long entidadeId, String entidadeNome, boolean responsavel) {
}
