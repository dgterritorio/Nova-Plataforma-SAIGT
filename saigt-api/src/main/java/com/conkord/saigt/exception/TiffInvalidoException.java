package com.conkord.saigt.exception;

import java.util.List;

/** GeoTIFF de uma peça gráfica fora das regras do item. Falha → 400. */
public class TiffInvalidoException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "DOC_TIFF_INVALIDO";

    public TiffInvalidoException(List<String> motivos) {
        super(CODIGO, "Documento inválido.", List.of(new CampoErro("ficheiro",
                "A peça gráfica não cumpre a especificação TIFF: " + String.join("; ", motivos) + ".")));
    }
}
