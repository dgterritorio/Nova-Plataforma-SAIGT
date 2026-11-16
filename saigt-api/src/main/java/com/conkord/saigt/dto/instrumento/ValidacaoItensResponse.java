package com.conkord.saigt.dto.instrumento;

import java.util.List;

/** Todos os itens do Processo, não só os do pedido. `todosRevistos` é verdadeiro sem nenhum `POR_VALIDAR`. */
public record ValidacaoItensResponse(
        List<ItemValidacaoResponse> itens,
        boolean todosRevistos
) {
}
