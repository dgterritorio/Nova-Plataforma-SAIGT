package com.conkord.saigt.dto.entidade;

import com.conkord.saigt.persistence.core.Entidade;

/** Uma opção da lista das entidades emitentes: só o necessário à lista (RGPD), sem NIF nem contactos. */
public record EntidadeEmitenteResponse(Long id, String nome) {

    public static EntidadeEmitenteResponse de(Entidade entidade) {
        return new EntidadeEmitenteResponse(entidade.getId(), entidade.getDescricaoSaigt());
    }
}
