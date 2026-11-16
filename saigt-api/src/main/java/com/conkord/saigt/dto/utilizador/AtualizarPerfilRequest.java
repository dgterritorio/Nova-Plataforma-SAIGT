package com.conkord.saigt.dto.utilizador;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Campo ausente ou nulo não é tocado; vazio limpa. Qualquer outro campo (o `nome`, o `cargo`…) fica em
 * {@link #getCamposNaoEditaveis()} para o pedido ser recusado em vez de ignorado.
 */
@Getter
@Setter
public class AtualizarPerfilRequest {

    private String email;
    private String telefone;
    private String morada;

    @Setter(lombok.AccessLevel.NONE)
    private final Set<String> camposNaoEditaveis = new LinkedHashSet<>();

    @JsonAnySetter
    public void campoNaoEditavel(String campo, Object valor) {
        camposNaoEditaveis.add(campo);
    }
}
