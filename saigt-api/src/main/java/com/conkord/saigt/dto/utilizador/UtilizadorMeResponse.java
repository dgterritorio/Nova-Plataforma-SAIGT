package com.conkord.saigt.dto.utilizador;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;

/**
 * `cargo` e `entidade` só nos INCM; `telefone`, `morada` e `perfilInterno` só nos AD_DGT (nulos na outra origem).
 * A entidade não traz a morada: a EF só pede nome, NIPC, telefone, email e o responsável.
 */
public record UtilizadorMeResponse(String nome, String email, String cargo, String telefone, String morada,
                                   String origemAutenticacao, PerfilInterno perfilInterno, EntidadeSessao entidade) {

    private static final String ORIGEM_INCM = "INCM";

    public record EntidadeSessao(String codigo, String nome, String tipoEntidade, String nipc, String telefone,
                                 String email, Responsavel responsavel) {

        /** {@code entidade} nula quando o código do token já não existe na SAIGT: fica só o que vem do token. */
        public static EntidadeSessao de(String codigo, String nome, String tipoEntidade, Entidade entidade) {
            if (entidade == null) {
                return new EntidadeSessao(codigo, nome, tipoEntidade, null, null, null, null);
            }
            Responsavel responsavel = entidade.getResponsavelNome() == null && entidade.getResponsavelEmail() == null
                    && entidade.getResponsavelCargo() == null ? null
                    : new Responsavel(entidade.getResponsavelNome(), entidade.getResponsavelEmail(),
                    entidade.getResponsavelCargo());
            return new EntidadeSessao(codigo, nome, tipoEntidade, entidade.getNipc(), entidade.getTelefone(),
                    entidade.getEmail(), responsavel);
        }
    }

    public record Responsavel(String nome, String email, String cargo) {
    }

    public static UtilizadorMeResponse de(Utilizador utilizador, EntidadeSessao entidade) {
        if (ORIGEM_INCM.equals(utilizador.getOrigemAutenticacao())) {
            return new UtilizadorMeResponse(utilizador.getNome(), utilizador.getEmail(), utilizador.getCargo(), null,
                    null, utilizador.getOrigemAutenticacao(), null, entidade);
        }
        return new UtilizadorMeResponse(utilizador.getNome(), utilizador.getEmail(), null, utilizador.getTelefone(),
                utilizador.getMorada(), utilizador.getOrigemAutenticacao(), utilizador.getPerfilInterno(), null);
    }
}
