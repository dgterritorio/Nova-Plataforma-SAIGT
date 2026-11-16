package com.conkord.saigt.exception;

import java.util.List;

/** ZIP com pastas, com entradas de nome inválido ou ilegível. Falha → 400. */
public class ZipInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "DOC_ZIP_INVALIDO";

    public ZipInvalidoException(String lista) {
        super(CODIGO, "Documento inválido.", List.of(new CampoErro("ficheiro",
                "O ZIP contém pastas ou ficheiros com nome inválido: " + lista + ".")));
    }
}
