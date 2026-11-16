package com.conkord.saigt.dto.entidade;

import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoEntidade;

import java.time.Instant;

/**
 * {@code nome} repete {@code descricaoIncm} para o ecrã atual de Gestão de Entidades; está obsoleto.
 * {@code atualizadoEm}, {@code telefone}, {@code email} e {@code utilizador*} (o responsável do termo de adesão)
 * vêm vazios numa entidade que nunca teve login INCM.
 * {@code contactoPrincipal} é o contacto institucional principal e ativo, não o responsável do termo de adesão.
 */
public record EntidadeResponse(Long id, String codigo, String nipc, String dico, String descricaoIncm,
                                String descricaoSaigt, TipoEntidade tipoEntidade, String telefone, String email,
                                boolean ativo, Instant atualizadoEm, String nome,
                                String utilizadorNome, String utilizadorEmail, String utilizadorCargo,
                                ContactoPrincipal contactoPrincipal) {

    public record ContactoPrincipal(Long id, String descricao, String email, String telefone) {
        public static ContactoPrincipal de(EntidadeContacto contacto) {
            return new ContactoPrincipal(contacto.getId(), contacto.getDescricao(), contacto.getEmail(),
                    contacto.getTelefone());
        }
    }

    public static EntidadeResponse de(Entidade entidade) {
        return de(entidade, null);
    }

    public static EntidadeResponse de(Entidade entidade, EntidadeContacto contactoPrincipal) {
        return new EntidadeResponse(entidade.getId(), entidade.getCodigo(), entidade.getNipc(), entidade.getDico(),
                entidade.getDescricaoIncm(), entidade.getDescricaoSaigt(), entidade.getTipoEntidade(),
                entidade.getTelefone(), entidade.getEmail(), entidade.isAtivo(), entidade.getAtualizadoEm(),
                entidade.getDescricaoIncm(), entidade.getResponsavelNome(), entidade.getResponsavelEmail(),
                entidade.getResponsavelCargo(),
                contactoPrincipal == null ? null : ContactoPrincipal.de(contactoPrincipal));
    }
}
