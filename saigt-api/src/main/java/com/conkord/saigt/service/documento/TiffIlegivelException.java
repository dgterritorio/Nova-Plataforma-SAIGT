package com.conkord.saigt.service.documento;

/**
 * O GDAL não consegue ler a entrada como TIFF (não reconhecida, corrompida ou truncada). Não sai do serviço: vira um
 * motivo de recusa.
 */
public class TiffIlegivelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TiffIlegivelException() {
        super("ficheiro TIFF ilegível");
    }
}
