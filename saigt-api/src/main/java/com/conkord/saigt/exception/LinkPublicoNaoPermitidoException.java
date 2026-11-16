package com.conkord.saigt.exception;

import java.util.List;

/** Link público pedido para um tipo de documento que não o permite. Falha → 400. */
public class LinkPublicoNaoPermitidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public LinkPublicoNaoPermitidoException(String nomeFicheiro) {
        super("LINK_PUBLICO_NAO_PERMITIDO", "O tipo de documento não permite link público.",
                List.of(new CampoErro("documentos", "Sem link público permitido: " + nomeFicheiro + ".")));
    }
}
