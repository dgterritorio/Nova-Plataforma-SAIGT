package com.conkord.saigt.exception;

import java.util.List;

/** `formato` em falta ou fora de PDF e EXCEL. Falha → 400. */
public class ExportacaoFormatoInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "EXPORTACAO_FORMATO_INVALIDO";

    public ExportacaoFormatoInvalidoException() {
        super(CODIGO, "Formato de exportação não suportado.",
                List.of(new CampoErro("formato", "Use PDF ou EXCEL.")));
    }
}
