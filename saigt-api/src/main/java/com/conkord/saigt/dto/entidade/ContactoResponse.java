package com.conkord.saigt.dto.entidade;

import com.conkord.saigt.persistence.configuracao.EntidadeContacto;

import java.time.Instant;

public record ContactoResponse(Long id, String email, String descricao, String telefone, boolean principal,
                               boolean ativo, Instant criadoEm) {

    public static ContactoResponse de(EntidadeContacto contacto) {
        return new ContactoResponse(contacto.getId(), contacto.getEmail(), contacto.getDescricao(),
                contacto.getTelefone(), contacto.isPrincipal(), contacto.isAtivo(), contacto.getCriadoEm());
    }
}
