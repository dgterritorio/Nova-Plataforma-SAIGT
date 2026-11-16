package com.conkord.saigt.dto.utilizador;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nunca inclui credenciais. `entidades` e `ultimoLoginEm` só são preenchidos em `listar()` (em lote) e `obter()`;
 * {@link #de} deixa-os vazios. `entidades` vem de `utilizador_entidade`, nunca de `auditoria`.
 * `email` é nulo nas contas `AD_DGT` sem email no AD. `ultimaAtuacaoEm` só vem na listagem filtrada por entidade,
 * e só para quem já atuou por ela.
 */
public record UtilizadorResponse(UUID id, String email, String origemAutenticacao, PerfilInterno perfilInterno,
                                  String nome, String cargo, List<EntidadeVinculoResponse> entidades,
                                  Instant ultimoLoginEm,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Instant ultimaAtuacaoEm) {

    public static UtilizadorResponse de(Utilizador utilizador) {
        return deCompleto(utilizador, List.of(), null, null);
    }

    public static UtilizadorResponse deCompleto(Utilizador utilizador, List<EntidadeVinculoResponse> entidades,
                                                Instant ultimoLoginEm, Instant ultimaAtuacaoEm) {
        return new UtilizadorResponse(utilizador.getId(), utilizador.getEmail(), utilizador.getOrigemAutenticacao(),
                utilizador.getPerfilInterno(), utilizador.getNome(), utilizador.getCargo(), entidades, ultimoLoginEm,
                ultimaAtuacaoEm);
    }
}
