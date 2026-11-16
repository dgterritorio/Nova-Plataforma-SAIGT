package com.conkord.saigt.dto.listavalores;

import jakarta.validation.constraints.NotBlank;

/**
 * Acrescenta um `codigo` a um `tipo` já existente; nunca cria um `tipo` novo. `paiId` só nos tipos com hierarquia
 * (Classe e Tipologia das servidões), onde é obrigatório.
 */
public record CriarListaValoresRequest(@NotBlank String tipo, @NotBlank String codigo, @NotBlank String descricao, int ordem,
                                       Long paiId) {

    public CriarListaValoresRequest(String tipo, String codigo, String descricao, int ordem) {
        this(tipo, codigo, descricao, ordem, null);
    }
}
